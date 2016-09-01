apidoc-generator
================

## Usage

Run with `sbt 'generator/run <port>'`.

## Specs

http://apidoc.me/movio/apidoc-generator-attributes/latest


## Kafka 0.10 Producer and Consumer

### Migration from 0.8

Old config:

    movio.cinema.item.kafka {
      producer {
        broker-connection-string : "localhost:9092""
        topic-instance = "prod"
      }
    }
    
    movio.cinema.item.kafka {
      consumer {
        topic-instance = "prod"
        tenants = []
        offset-storage-type = "kafka"
        offset-storage-dual-commit = false
        timeout.ms = "1000"
        zookeeper.connection = "{zkServer.getConnectString}"
      }
    }


New Config:

    movio.cinema.item.kafka {
      producer {
        bootstrap.servers: "localhost:9092""
        topic.instance = "prod"
      }
    }
    
    movio.cinema.item.kafka {
      consumer {
        bootstrap.servers: "localhost:9092""
        topic.instance = "prod"
        tenants = []
        poll.timeout = 1000
      }
    }

build.sbt, add the following:

    // Update aggregates

    lazy val root = project
      .in( file(".") )
      .aggregate(lib, playLib, kafkaLib_0_8, kafkaLib_0_10)
      .settings(
        publish := {}
      )

    // Add new lib project

    lazy val kafkaLib_0_10 = project
      .in(file("kafka-lib_0_10"))
      .dependsOn(lib)
      .aggregate(lib)
      .settings(commonSettings: _*)
      .settings(
        libraryDependencies ++= Seq(
          "com.typesafe.play" %% "play-json" % PlayVersion,
          "movio.api" %% "kafka-lib_0_10" % "0.3.2",
          "movio.core" %% "movio-core-utils" % "0.1.0",
          "mm" %% "testinglib" % "1.0.1" % Test,
          "mc" %% "kafka-testkit" % "4.0.0" % Test
        )
      )
      
.apidoc - add the following after kakfa_0_8:...

    kafka_0_10: kafka-lib_0_10/src/main/generated
    kafka_0_10_tests: kafka-lib_0_10/src/test/generated
Then run apidoc update
      
Producer Usage:

    // Producers should be closed when no longer used. Not needed if they
    // remain running for the entirity of the service
    producer.close()  // new

Consumer Usage:

    // consumers shutdown chnaged
    consumer.shutdown() // old
    consumer.close() // new
    
Update generated services:


