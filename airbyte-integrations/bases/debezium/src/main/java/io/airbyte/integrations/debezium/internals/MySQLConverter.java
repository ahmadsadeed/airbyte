/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.debezium.internals;

import io.debezium.spi.converter.CustomConverter;
import io.debezium.spi.converter.RelationalColumn;
import java.util.Arrays;
import java.util.Properties;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a custom debezium converter used in MySQL to handle the DATETIME data type. We need a
 * custom converter cause by default debezium returns the DATETIME values as numbers. We need to
 * convert it to proper format. Ref :
 * https://debezium.io/documentation/reference/1.4/development/converters.html This is built from
 * reference with {@link io.debezium.connector.mysql.converters.TinyIntOneToBooleanConverter} If you
 * rename this class then remember to rename the datetime.type property value in
 * io.airbyte-integrations.source.mysql.MySqlCdcProperties#getDebeziumProperties() (If you don't
 * rename, a test would still fail but it might be tricky to figure out where to change the property
 * name)
 */
public class MySQLConverter implements CustomConverter<SchemaBuilder, RelationalColumn> {

  private static final Logger LOGGER = LoggerFactory.getLogger(MySQLConverter.class);

  private final String[] DATE_TYPES = {"DATE", "DATETIME", "TIME"};
  private final String[] TEXT_TYPES = {"VARCHAR", "VARBINARY", "BLOB", "TEXT", "LONGTEXT", "TINYTEXT", "MEDIUMTEXT"};

  @Override
  public void configure(final Properties props) {}

  @Override
  public void converterFor(final RelationalColumn field, final ConverterRegistration<SchemaBuilder> registration) {
    if (Arrays.stream(DATE_TYPES).anyMatch(s -> s.equalsIgnoreCase(field.typeName()))) {
      registerDate(field, registration);
    } else if (Arrays.stream(TEXT_TYPES).anyMatch(s -> s.equalsIgnoreCase(field.typeName()))) {
      registerText(field, registration);
    }
  }

  private void registerText(final RelationalColumn field, final ConverterRegistration<SchemaBuilder> registration) {
    registration.register(SchemaBuilder.string(), x -> {
      if (x == null) {
        if (field.isOptional()) {
          return null;
        } else if (field.hasDefaultValue()) {
          return field.defaultValue();
        }
        return null;
      }

      if (x instanceof byte[]) {
        return new String((byte[]) x);
      } else {
        return x.toString();
      }
    });
  }

  private void registerDate(final RelationalColumn field, final ConverterRegistration<SchemaBuilder> registration) {
    registration.register(SchemaBuilder.string(),
        x -> x == null ? DebeziumConverterUtils.convertDefaultValue(field) : DebeziumConverterUtils.convertDate(x));
  }

}
