package com.linkedin.datastream.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.apache.commons.lang.Validate;

public class AvroMessageEncoderUtil {
  public static final byte MAGIC_BYTE = 0x0;

  /**
   * generates the md5 hash of the schemaId and appends it to the given byte array.
   * the byte array representing the payload of a BrooklinEnvelope
   *
   * This is done so when the client decodes the payload, it will contain a schemaId which
   * can be used to retrieve the schema from the Schema Registry
   *
   * @param schemaId
   * @param value
   * @return
   * @throws IOException
   */
  public static byte[] encode(String schemaId, byte[] value) throws IOException {
    Validate.notNull(value, "cannot encode null byte array, schemaId: " + schemaId);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(MAGIC_BYTE);
    byte[] md5Bytes = hexToMd5(schemaId);
    out.write(md5Bytes);
    out.write(value);
    return out.toByteArray();
  }

  /**
   * generates the md5 hash of the schemaId and appends it to the given byte array.
   * the byte array representing the payload of a BrooklinEnvelope
   *
   * This is done so when the client decodes the payload, it will contain a schemaId which
   * can be used to retrieve the schema from the Schema Registry
   *
   * This method also converts an IndexedRecord into a byte array first
   *
   * @param schemaId
   * @param record
   * @return
   * @throws IOException
   */
  public static byte[] encode(String schemaId, IndexedRecord record) throws AvroEncodingException {
    Validate.notNull(record, "cannot encode null Record, schemaId: " + schemaId);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(MAGIC_BYTE);
    byte[] md5Bytes = hexToMd5(schemaId);

    try {
      out.write(md5Bytes);
      BinaryEncoder encoder = new BinaryEncoder(out);
      DatumWriter<org.apache.avro.generic.IndexedRecord> writer;
      if (record instanceof SpecificRecord) {
        writer = new SpecificDatumWriter<IndexedRecord>(record.getSchema());
      } else {
        writer = new GenericDatumWriter<IndexedRecord>(record.getSchema());
      }
      writer.write(record, encoder);
      encoder.flush(); //encoder may buffer
    } catch (IOException e) {
      throw new AvroEncodingException(e);
    }

    return out.toByteArray();
  }

  /**
   * When registering a Schema with some Schema Registry it should return a Hex value
   * to be used to indentify that schema.
   *
   * @param schema
   * @return
   */
  public static String schemaToHex(Schema schema) {
    byte[] utf8Bytes = utf8(schema.toString(false));
    byte[] md5Bytes = md5(utf8Bytes);
    return hex(md5Bytes);
  }

  /**
   * Converts a String into utf8
   */
  private static byte[] utf8(String s) {
    try {
      return s.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("This can't happen");
    }
  }


  private static byte[] md5(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("md5");
      return digest.digest(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("This can't happen.", e);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(2 * bytes.length);
    for (int i = 0; i < bytes.length; i++) {
      String hexString = Integer.toHexString(0xFF & bytes[i]);
      if (hexString.length() < 2) {
        hexString = "0" + hexString;
      }
      builder.append(hexString);
    }
    return builder.toString();
  }


  private static byte[] hexToMd5(String s) {
    int len = s.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
    }
    return data;
  }
}