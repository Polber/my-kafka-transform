# my-kafka-transform

See https://github.com/Polber/beam-yaml-xlang for more info on Beam YAML and Xlang.

## Building the JAR

1. Clone the repo
2. Package the JAR by running:
```
mvn clean package
```
This will place a jar named "my-kafka-transform-bundled-1.0.jar" in the `target/` directory.

3. Use in Beam YAML pipeline:
```
pipeline:
  type: chain
  transforms:
    - type: ReadFromKafkaCustom
      config:
        topic: ...
        format: ...
        bootstrap_servers: ...
        consumer_config_updates:
          sasl.jaas.config: '... someKey="{secret projects/12345/secrets/my-secret/versions/1}" ...;'
          ...
        auto_offset_reset_config: latest
    ...
    
providers:
  - type: javaJar
    config:
      jar: /path/to/my-kafka-transform-bundled-1.0.jar
    transforms:
      ReadFromKafkaCustom: my-kafka-transform:schematransform:org.example:custom_kafka_read:v1
```