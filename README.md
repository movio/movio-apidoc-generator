apidoc-generator
================

## Usage

Run with `sbt 'generator/run <port>'`.

## Custom Attributes

### Kafka Props

    "models": {
      "kafka_person": {
        "attributes": [
          { "name": "kafka_props",
            "value": {
              "data_type": "person",
              "message_key": "v0.id",
              "topic": "s\"mc-person-core-${tenant}\""
            }
          }
        ],
        "fields": [...]
      }
    }

`kafka_props` defines addition details for generating kafka json wrappers
  `data_type` is the model that is being wrapped
  `message_key` [scala] is the scala code use to create the kafka message id. Ex:w

