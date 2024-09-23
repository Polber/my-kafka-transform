# Packaging a custom KafkaIO provider and Using in Beam YAML

<!-- TOC -->
* [Creating a Beam Java Transform Catalog and Using in Beam YAML](#creating-a-beam-java-transform-catalog-and-using-in-beam-yaml)
  * [Prerequisites](#prerequisites)
  * [Background](#background)
  * [Overview](#overview)
  * [Project Structure](#project-structure)
  * [Modifying the pom.xml file](#modifying-the-pomxml-file)
  * [Building the Transform Catalog JAR](#building-the-transform-catalog-jar)
  * [Defining the Transform in Beam YAML](#defining-the-transform-in-beam-yaml)
<!-- TOC -->


## Prerequisites

To complete this tutorial, you must have the following software installed:
* Java 11 or later
* Apache Maven 3.6 or later


## Background

The concepts used in this repo are based heavily on the Beam YAML Cross Language tutorial [repo](https://github.com/Polber/beam-yaml-xlang).


## Overview

The purpose of this repo is to demenstrate how to modify and package a custom Beam KafkaIO provider to use in Beam YAML. This
is useful when packaging a custom `kafka_clients` JAR and/or modifying the KafkaIO provider source code.


## Project Structure

The project structure for this tutorial is as follows:
```
my-kafka-transform
├── pom.xml
└── src
    └── main
        └── java
            └── org
                └── example
                    ├── KafkaReadSchemaTransformConfiguration.java
                    └── KafkaReadSchemaTransformProvider.java
```

Here is a brief description of each file:
* **pom.xml:** The Maven project configuration file.
* **KafkaReadSchemaTransformProvider.java:** The Java class that contains the KafkaIO provider source code (mirrored
from Beam repo).
* **KafkaReadSchemaTransformConfiguration.java:** The Java class that contains the `Configuration` that is used to
define the input parameters to the transform

## Modifying the pom.xml file

A `pom.xml` file, which stands for Project Object Model, is an essential file used in Maven projects. It's an XML file
that contains all the critical information about a project, including its configuration details for Maven to build it
successfully.  This file specifies things like the project's name, version, dependencies on other libraries, and how
the project should be packaged (e.g., JAR file).

This repo defines a custom `KafkaReadSchemaTransformProvider` with minor changes. However, if you wish to use the existing
`KafkaReadSchemaTransformProvider` release with current Beam version and only intend to package a spceific version of 
`kafka_clients`, simply remove the following dependency on KafkaIO:
```
<dependency>
  <groupId>org.apache.beam</groupId>
  <artifactId>beam-sdks-java-io-kafka</artifactId>
  <version>${beam.version}</version>
</dependency>
```
and replace with the dependency on the Beam Cross-Language IO transform(s):
```
<dependency>
  <groupId>org.apache.beam</groupId>
  <artifactId>beam-sdks-java-io-expansion-service</artifactId>
  <version>${beam.version}</version>
</dependency>
```
Note: You can also optionally remove the `src` directory as none of that code is needed.


## Building the Transform Catalog JAR
At this point, you should have all the code as defined in the previous section ready to go, and it is now time to build
the JAR that will be provided to the Beam YAML pipeline.

From the root directory, run the following command:

```
mvn package
```

This will create a JAR under `target` called `my-kafka-transform-bundled-1.0-SNAPSHOT.jar` that contains the
`KafkaReadSchemaTransformProvider` along with its dependencies and the external transform service. The external expansion
service is what will be invoked by the Beam YAML SDK to import the transform schema and run the expansion service for
the transform.

**Note:** The name of the jar is configurable using the `finalName` tag in the `maven-shade-plugin` configuration.

## Defining the Transform in Beam YAML

Now that you have a JAR file that contains the transform catalog, it is time to include it as part of your Beam YAML
pipeline. This is done using <code>[providers](https://beam.apache.org/documentation/sdks/yaml/#providers)</code> -
these providers allow one to define a suite of transforms in a given JAR or python package that can be used within the
Beam YAML pipeline.

We will be utilizing the `javaJar` provider as we are planning to keep the names of the config parameters as they are defined in the transform.

For our example, that looks as follows:
```yaml
providers:
  - type: javaJar
    config:
      jar: my-kafka-transform-bundled-1.0-SNAPSHOT.jar
    transforms:
      MyReadFromKafka: "beam:schematransform:org.apache.beam:kafka_read:v2"
```

Now, `MyReadFromKafka` can be defined as a transform in the Beam YAML pipeline.

A full example:
```yaml
pipeline:
  type: chain
  transforms:
    - type: MyReadFromKafka
      config:
        ...
    ...

providers:
  - type: javaJar
    config:
      jar: my-kafka-transform-bundled-1.0-SNAPSHOT.jar
    transforms:
      MyReadFromKafka: "beam:schematransform:org.apache.beam:kafka_read:v2"
```

If you have Beam Python installed, you can test this pipeline out locally with

```
python -m apache_beam.yaml.main --yaml_pipeline_file=pipeline.yaml
```

or if you have gcloud installed you can run this on dataflow with

```
gcloud dataflow yaml run $JOB_NAME --yaml-pipeline-file=pipeline.yaml --region=$REGION
```

(Note in this case you will need to upload your jar to a gcs bucket or
publish it elsewhere as a globally-accessible URL so it is available to
the dataflow service.)
