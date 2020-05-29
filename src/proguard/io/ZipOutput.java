/*
 * ProGuardCORE -- library to process Java bytecode.
 *
 * Copyright (c) 2002-2020 Guardsquare NV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package proguard.io;

import proguard.util.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * This class writes zip data to a given output stream. It returns a new
 * output stream for each zip entry that is opened. An entry can be compressed
 * or uncompressed. Uncompressed entries can be aligned to a multiple of a
 * given number of bytes.
 * <p/>
 * Multiple entries and output streams can be open at the same time. The entries
 * are added to the central directory in the order in which they are opened, but
 * the corresponding data are only written when their output streams are closed.
 * <p/>
 * The code automatically computes the CRC and lengths of the data, for
 * compressed and uncompressed data.
 *
 * @author Eric Lafortune
 */
public class ZipOutput
{
    private static final int MAGIC_LOCAL_FILE_HEADER             = 0x04034b50;
    private static final int MAGIC_CENTRAL_DIRECTORY_FILE_HEADER = 0x02014b50;
    private static final int MAGIC_END_OF_CENTRAL_DIRECTORY      = 0x06054b50;

    private static final int VERSION              = 10;
    private static final int GENERAL_PURPOSE_FLAG =  0;
    private static final int METHOD_UNCOMPRESSED  =  0;
    private static final int METHOD_COMPRESSED    =  8;

    private static final boolean DEBUG = false;


    protected     DataOutputStream outputStream;
    private final int              uncompressedAlignment;

    private final String           comment;

    private List zipEntries    = new ArrayList();
    private Set  zipEntryNames = new HashSet();


    // Regular constructors.

    /**
     * Creates a new ZipOutput.
     * @param outputStream the output stream to which the zip data will be
     *                     written.
     */
    public ZipOutput(OutputStream outputStream)
    {
        this(outputStream, 1);
    }


    /**
     * Creates a new ZipOutput that aligns uncompressed entries.
     * @param outputStream           the output stream to which the zip data will
     *                               be written.
     * @param uncompressedAlignment the default alignment of uncompressed data.
     */
    public ZipOutput(OutputStream outputStream,
                     int          uncompressedAlignment)
    {
        this(outputStream,
             uncompressedAlignment,
             null);
    }


    /**
     * Creates a new ZipOutput that aligns uncompressed entries and contains a comment.
     *
     * @param outputStream          the output stream to which the zip data will
     *                              be written.
     * @param uncompressedAlignment the default alignment of uncompressed data.
     * @param comment               optional comment for the entire zip file.
     */
    public ZipOutput(OutputStream outputStream,
                     int          uncompressedAlignment,
                     String       comment)
    {
        this(new DataOutputStream(outputStream),
             uncompressedAlignment,
             comment);
    }


    /**
     * Creates a new ZipOutput that aligns uncompressed entries and contains a comment.
     *
     * @param outputStream          the output stream to which the zip data will
     *                              be written.
     * @param uncompressedAlignment the default alignment of uncompressed data.
     * @param comment               optional comment for the entire zip file.
     */
    public ZipOutput(DataOutputStream outputStream,
                     int              uncompressedAlignment,
                     String           comment)
    {
        this.outputStream          = outputStream;
        this.uncompressedAlignment = uncompressedAlignment;
        this.comment               = comment;
    }


    // These constructors write out a header immediately.

    /**
     * Creates a new ZipOutput that aligns uncompressed entries.
     * @param outputStream          the output stream to which the zip data will
     *                              be written.
     * @param header                an optional header for the zip file.
     * @param uncompressedAlignment the requested alignment of uncompressed data.
     */
    public ZipOutput(OutputStream outputStream,
                     byte[]       header,
                     int          uncompressedAlignment)
    throws IOException
    {
        this(outputStream,
             header,
             uncompressedAlignment,
             null);
    }


    /**
     * Creates a new ZipOutput that aligns uncompressed entries and contains a comment.
     *
     * @param outputStream          the output stream to which the zip data will be written.
     * @param header                an optional header for the zip file.
     * @param uncompressedAlignment the requested alignment of uncompressed data.
     * @param comment               optional comment for the entire zip file.
     */
    public ZipOutput(OutputStream  outputStream,
                     byte[]        header,
                     int           uncompressedAlignment,
                     String        comment)
    throws IOException
    {
        this(new DataOutputStream(outputStream),
             header,
             uncompressedAlignment,
             comment);
    }


    /**
     * Creates a new ZipOutput that aligns uncompressed entries and contains a comment.
     *
     * @param outputStream          the output stream to which the zip data will be written.
     * @param header                an optional header for the zip file.
     * @param uncompressedAlignment the requested alignment of uncompressed data.
     * @param comment               optional comment for the entire zip file.
     */
    public ZipOutput(DataOutputStream outputStream,
                     byte[]           header,
                     int              uncompressedAlignment,
                     String           comment)
    throws IOException
    {
        this.outputStream          = outputStream;
        this.uncompressedAlignment = uncompressedAlignment;
        this.comment               = comment;
        if (header != null)
        {
            outputStream.write(header);
        }
    }


    /**
     * Creates a new zip entry, returning an output stream to write its data.
     * It is the caller's responsibility to close the output stream.
     * @param name             the name of the zip entry.
     * @param compress         specifies whether the entry should be
     *                         compressed with the default
     *                         alignment.
     * @param modificationTime the modification date and time of the zip
     *                         entry, in DOS format.
     * @return an output stream for writing the data of the zip entry.
     */
    public OutputStream createOutputStream(String  name,
                                           boolean compress,
                                           int     modificationTime)
    throws IOException
    {
        return createOutputStream(name,
                                  compress,
                                  uncompressedAlignment,
                                  modificationTime,
                                  null,
                                  null);
    }


    /**
     * Creates a new zip entry, returning an output stream to write its data.
     * It is the caller's responsibility to close the output stream.
     * @param name                  the name of the zip entry.
     * @param compress              specifies whether the entry should be
     *                              compressed.
     * @param uncompressedAlignment the requested alignment of uncompressed
     *                              data.
     * @param modificationTime      the modification date and time of the zip
     *                              entry, in DOS format.
     * @return an output stream for writing the data of the zip entry.
     */
    public OutputStream createOutputStream(String  name,
                                           boolean compress,
                                           int     uncompressedAlignment,
                                           int     modificationTime)
    throws IOException
    {
        return createOutputStream(name,
                                  compress,
                                  uncompressedAlignment,
                                  modificationTime,
                                  null,
                                  null);
    }


    /**
     * Creates a new zip entry, returning an output stream to write its data.
     * It is the caller's responsibility to close the output stream.
     * @param name                  the name of the zip entry.
     * @param compress              specifies whether the entry should be
     *                              compressed.
     * @param uncompressedAlignment the requested alignment of uncompressed
     *                              data.
     * @param modificationTime      the modification date and time of the zip
     *                              entry, in DOS format.
     * @param extraField            optional extra field data. These should
     *                              contain chunks, each with a short ID, a
     *                              short length (little endian), and their
     *                              corresponding data. The IDs 0-31 are
     *                              reserved for Pkware. Java's jar tool just
     *                              specifies an ID 0xcafe on its first entry.
     * @param comment               an optional comment.
     * @return an output stream for writing the data of the zip entry.
     */
    public OutputStream createOutputStream(String  name,
                                           boolean compress,
                                           int     uncompressedAlignment,
                                           int     modificationTime,
                                           byte[]  extraField,
                                           String  comment)
    throws IOException
    {
        // Check if the name hasn't been used yet.
        if (!zipEntryNames.add(name))
        {
            throw new IOException("Duplicate jar entry ["+name+"]");
        }

        ZipEntry entry = new ZipEntry(name,
                                      compress,
                                      uncompressedAlignment,
                                      modificationTime,
                                      extraField,
                                      comment);

        // Add the entry to the list that will be put in the central directory.
        zipEntries.add(entry);

        return entry.createOutputStream();
    }


    /**
     * Closes the zip archive, writing out its central directory and closing
     * the underlying output stream.
     */
    public void close() throws IOException
    {
        long centralDirectoryOffset = writeStartOfCentralDirectory();

        close(centralDirectoryOffset);
    }


    /**
     * Closes the zip archive, writing out its central directory and closing
     * the underlying output stream.
     */
    public void close(long centralDirectoryOffset) throws IOException
    {
        // Write the central directory.
        long centralDirectorySize = writeEntriesOfCentralDirectory();

        writeEndOfCentralDirectory(centralDirectoryOffset,
                                   centralDirectorySize);

        // Close the underlying output stream.
        outputStream.close();

        // Make sure the archive can't be used any further.
        outputStream  = null;
        zipEntries    = null;
        zipEntryNames = null;
    }


    /**
     * Returns the current size of the data written to the output stream.
     */
    protected long size()
    {
        return outputStream.size();
    }


    /**
     * Starts the central directory.
     * @return the current position in the output stream.
     */
    protected long writeStartOfCentralDirectory()
    {
        if (DEBUG)
        {
            System.out.println("ZipOutput.writeStartOfCentralDirectory");
        }

        // The central directory as such doesn't have a header.
        return outputStream.size();
    }


    /**
     * Writes the zip entries in the central directory.
     * @return the total size of the directory entries.
     */
    protected long writeEntriesOfCentralDirectory() throws IOException
    {
        if (DEBUG)
        {
            System.out.println("ZipOutput.writeEntriesOfCentralDirectory ("+zipEntries.size()+" entries)");
        }

        long offset = outputStream.size();

        for (int index = 0; index < zipEntries.size(); index++)
        {
            ZipEntry entry = (ZipEntry)zipEntries.get(index);

            entry.writeCentralDirectoryFileHeader();
        }

        return outputStream.size() - offset;
    }


    /**
     * Ends the central directory.
     * @param centralDirectoryOffset the offset of the central directory.
     * @param centralDirectorySize   the size of the central directory, not
     *                               counting the empty header and the trailer.
     */
    protected void writeEndOfCentralDirectory(long centralDirectoryOffset,
                                              long centralDirectorySize) throws IOException
    {
        if (DEBUG)
        {
            System.out.println("ZipOutput.writeEndOfCentralDirectory ("+zipEntries.size()+" entries)");
        }

        writeInt(MAGIC_END_OF_CENTRAL_DIRECTORY);
        writeShort(0);                    // Number of this disk.
        writeShort(0);                    // Number of disk with central directory.
        writeShort(zipEntries.size());    // Number of records on this disk.
        writeShort(zipEntries.size());    // Total number of records.
        writeInt(centralDirectorySize);   // Size of central directory, in bytes.
        writeInt(centralDirectoryOffset); // Offset of central directory.

        if (comment == null)
        {
            // No comment.
            writeShort(0);
        }
        else
        {
            // Comment length and comment.
            byte[] commentBytes = StringUtil.getModifiedUtf8Bytes(comment);
            writeShort(commentBytes.length);
            outputStream.write(commentBytes);
        }
    }


    /**
     * This class represents a zip entry in its enclosing zip file. It can
     * provide an output stream and write its headers and its data to the main
     * zip output stream. In fact, it automatically writes its local header and
     * data when the output stream is closed.
     */
    private class ZipEntry
    {
        private boolean compressed;
        private int     uncompressedAlignment;
        private int     modificationTime;
        private int     crc;
        private long    compressedSize;
        private long    uncompressedSize;
        private long    offset;
        private String  name;
        private byte[]  extraField;
        private String  comment;


        /**
         * Creates a new zip entry, returning output stream to write its data.
         * It is the caller's responsibility to close the output stream.
         * @param name                  the name of the zip entry.
         * @param compressed            specifies whether the entry should be
         *                              compressed.
         * @param uncompressedAlignment the requested alignment of uncompressed
         *                              data.
         * @param modificationTime      the modification date and time of the
         *                              zip entry, in DOS format.
         * @param extraField            optional extra field data. These should
         *                              contain chunks, each with a short ID,
         *                              a short length (little endian), and
         *                              their corresponding data. The IDs 0-31
         *                              are reserved for Pkware. Java's jar tool
         *                              just specifies an ID 0xcafe on its first
         *                              entry.
         * @param comment               an optional comment.
         * @return an output stream for writing the zip data.
         */
        private ZipEntry(String  name,
                         boolean compressed,
                         int     uncompressedAlignment,
                         int     modificationTime,
                         byte[]  extraField,
                         String  comment)
        {
            this.name                  = name;
            this.compressed            = compressed;
            this.uncompressedAlignment = uncompressedAlignment;
            this.modificationTime      = modificationTime;
            this.extraField            = extraField;
            this.comment               = comment;
        }


        public OutputStream createOutputStream() throws IOException
        {
            return compressed ?
                new CompressedZipEntryOutputStream() :
                new UncompressedZipEntryOutputStream();
        }


        /**
         * Writes the local file header, which precedes the data, to the main
         * zip output stream.
         */
        private void writeLocalFileHeader() throws IOException
        {
            if (DEBUG)
            {
                System.out.println("ZipOutput.writeLocalFileHeader ["+name+"] (compressed = "+compressed+", offset = "+offset+", "+compressedSize+"/"+uncompressedSize+" bytes)");
            }

            writeInt(MAGIC_LOCAL_FILE_HEADER);
            writeShort(VERSION);
            writeShort(GENERAL_PURPOSE_FLAG);
            writeShort(compressed ? METHOD_COMPRESSED : METHOD_UNCOMPRESSED);
            writeInt(modificationTime);
            writeInt(crc);
            writeInt(compressedSize);
            writeInt(uncompressedSize);

            byte[] nameBytes = StringUtil.getModifiedUtf8Bytes(name);
            int nameLength       = nameBytes.length;
            int extraFieldLength = extraField == null ? 0 : extraField.length;

            writeShort(nameLength);

            // Compute the required delta to get uncompressed data aligned.
            // These filler bytes will be appended to the extra field.
            int alignmentDelta = 0;
            if (!compressed)
            {
                long dataOffset = outputStream.size() + 2 + nameLength + extraFieldLength;
                alignmentDelta = (int)(dataOffset % uncompressedAlignment);
                if (alignmentDelta > 0)
                {
                    alignmentDelta = uncompressedAlignment - alignmentDelta;
                }
            }

            writeShort(extraFieldLength + alignmentDelta);

            outputStream.write(nameBytes);

            if (extraField != null)
            {
                outputStream.write(extraField);
            }

            // Now append the filler bytes, as part of the extra field,
            // right before the data that will be added after the header.
            if (alignmentDelta > 0)
            {
                outputStream.write(new byte[alignmentDelta]);
            }
        }


        /**
         * Writes the file header for the central directory to the main zip
         * output stream.
         */
        public void writeCentralDirectoryFileHeader() throws IOException
        {
            if (DEBUG)
            {
                System.out.println("ZipOutput.writeCentralDirectoryFileHeader ["+name+"] (compressed = "+compressed+", offset = "+offset+", "+compressedSize+"/"+uncompressedSize+" bytes)");
            }

            writeInt(MAGIC_CENTRAL_DIRECTORY_FILE_HEADER);
            writeShort(VERSION); // Creation version.
            writeShort(VERSION); // Extraction Version.
            writeShort(GENERAL_PURPOSE_FLAG);
            writeShort(compressed ? METHOD_COMPRESSED : METHOD_UNCOMPRESSED);
            writeInt(modificationTime);
            writeInt(crc);
            writeInt(compressedSize);
            writeInt(uncompressedSize);

            byte[] nameBytes    = StringUtil.getModifiedUtf8Bytes(name);
            byte[] commentBytes = comment == null ? null :
                                  StringUtil.getModifiedUtf8Bytes(comment);

            writeShort(nameBytes.length);
            writeShort(extraField   == null ? 0 : extraField.length);
            writeShort(commentBytes == null ? 0 : commentBytes.length);
            writeShort(0); // Disk number of file start.
            writeShort(0); // Internal file attributes.
            writeInt(0);   // External file attributes.
            writeInt(offset);
            outputStream.write(nameBytes);
            if (extraField != null)
            {
                outputStream.write(extraField);
            }

            if (commentBytes != null)
            {
                outputStream.write(commentBytes);
            }
        }


        /**
         * This OutputStream writes its uncompressed zip entry out to its zip
         * output stream when it is closed.
         */
        private class UncompressedZipEntryOutputStream extends ByteArrayOutputStream
        {
            private CRC32 crc32 = new CRC32();


            private UncompressedZipEntryOutputStream()
            {
                super(16 * 1024);
            }


            // Overridden methods for OutputStream.

            @Override
            public void write(int b)
            {
                super.write(b);

                crc32.update(b);
            }


            //public void write(byte[] b) throws IOException
            //{
            //    // The super implementation delegates to the method below.
            //    super.write(b);
            //}


            @Override
            public void write(byte[] b, int off, int len)
            {
                super.write(b, off, len);

                crc32.update(b, off, len);
            }


            @Override
            public void close() throws IOException
            {
                super.close();

                byte[] bytes = super.toByteArray();

                offset           = outputStream.size();
                crc              = (int)crc32.getValue();
                compressedSize   = bytes.length;
                uncompressedSize = bytes.length;

                writeLocalFileHeader();
                outputStream.write(bytes);
            }
        }


        /**
         * This OutputStream writes its compressed zip entry out to its zip
         * output stream when it is closed.
         */
        private class CompressedZipEntryOutputStream extends DeflaterOutputStream
        {
            private CRC32 crc32 = new CRC32();


            private CompressedZipEntryOutputStream()
            {
                super(new ByteArrayOutputStream(16 * 1024),
                      new Deflater(Deflater.BEST_COMPRESSION, true),
                      1024);
            }


            // Overridden methods for OutputStream.

            //public void write(int b) throws IOException
            //{
            //    // The super implementation delegates to the method below.
            //    super.write(b);
            //}
            //
            //
            //public void write(byte[] b) throws IOException
            //{
            //    // The super implementation delegates to the method below.
            //    super.write(b);
            //}


            @Override
            public void write(byte[] b, int off, int len) throws IOException
            {
                super.write(b, off, len);

                crc32.update(b, off, len);
                uncompressedSize += len;
            }


            @Override
            public void close() throws IOException
            {
                // Make sure the memory is freed. [JDK-4797189]
                super.finish();
                super.def.end();
                super.close();

                ByteArrayOutputStream byteArrayOutputStream =
                    (ByteArrayOutputStream)super.out;

                byte[] compressedBytes = byteArrayOutputStream.toByteArray();

                offset         = outputStream.size();
                crc            = (int)crc32.getValue();
                compressedSize = compressedBytes.length;

                writeLocalFileHeader();
                outputStream.write(compressedBytes);
            }
        }
    }


    // Small utility methods.

    /**
     * Writes out a little-endian short value to the zip output stream.
     */
    protected void writeShort(int value) throws IOException
    {
        outputStream.write(value);
        outputStream.write(value >>> 8);
    }


    /**
     * Writes out a little-endian int value to the zip output stream.
     */
    protected void writeInt(int value) throws IOException
    {
        outputStream.write(value);
        outputStream.write(value >>>  8);
        outputStream.write(value >>> 16);
        outputStream.write(value >>> 24);
    }


    /**
     * Writes out a little-endian int value to the zip output stream.
     */
    protected void writeInt(long value) throws IOException
    {
        outputStream.write((int)value);
        outputStream.write((int)(value >>>  8));
        outputStream.write((int)(value >>> 16));
        outputStream.write((int)(value >>> 24));
    }


    /**
     * Writes out a little-endian long value to the zip output stream.
     */
    protected void writeLong(long value) throws IOException
    {
        outputStream.write((int)value);
        outputStream.write((int)(value >>>  8));
        outputStream.write((int)(value >>> 16));
        outputStream.write((int)(value >>> 24));
        outputStream.write((int)(value >>> 32));
        outputStream.write((int)(value >>> 40));
        outputStream.write((int)(value >>> 48));
        outputStream.write((int)(value >>> 56));
    }


    /**
     * Provides a simple test for this class, creating a zip file with the
     * given name and a few aligned/compressed/uncompressed zip entries.
     */
    public static void main(String[] args)
    {
        try
        {
            ZipOutput output =
                new ZipOutput(new FileOutputStream(args[0]), null, 4, "Main file comment");

            PrintWriter printWriter1 =
                new PrintWriter(output.createOutputStream("file1.txt", false, 1, 0, new byte[] { 0x34, 0x12, 4, 0, 0x48, 0x65, 0x6c, 0x6c, 0x6f }, "Comment"));
            printWriter1.println("This is file 1.");
            printWriter1.println("Hello, world!");
            printWriter1.close();

            PrintWriter printWriter2 =
                new PrintWriter(output.createOutputStream("file2.txt", true, 1, 0, null, "Another comment"));
            printWriter2.println("This is file 2.");
            printWriter2.println("Hello, world!");
            printWriter2.close();

            PrintWriter printWriter3 =
                new PrintWriter(output.createOutputStream("file3.txt", false, 1, 0, null, "Last comment"));
            printWriter3.println("This is file 3.");
            printWriter3.println("Hello, world!");
            printWriter3.close();

            output.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
