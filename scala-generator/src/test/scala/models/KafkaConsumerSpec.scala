package scala.models

import scala.generator._

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import org.scalatest.{ ShouldMatchers, FunSpec }

class KafkaConsumerSpec extends FunSpec with ShouldMatchers {
  import KafkaUtil._
  import CaseClassUtil._

  val json = models.TestHelper.buildJson("""
      "imports": [],
      "headers": [],
      "info": [],
      "enums": [],
      "unions": [],
      "attributes": [],
      "models": [
        {
          "name": "member",
          "attributes": [],
          "plural": "members",
          "fields": [
            { "name": "id", "type": "long", "required": true, "attributes": [] },
            { "name": "email", "type": "string", "required": true, "attributes": [] },
            { "name": "name", "type": "string", "required": false, "attributes": [] },
            { "name": "foo", "type": "string", "required": true, "attributes": [] }
          ]
        },
        {
          "name": "kafka_member",
          "plural": "kafka_members",
          "fields": [{ "name": "v0", "type": "member", "required": true, "attributes": [] }],
          "attributes": [
            { "name": "kafka_props",
              "value": {
                "data_type": "member",
                "message_generate_key": "v0.id",
                "topic": "s\"mc.data.member-internal.$instance.$tenant\""
              }
            }
          ]
        }
      ],
      "resources": []
  """)

  lazy val service = models.TestHelper.service(json)
  lazy val form = InvocationForm(service)

  it("generates kafka consumer") {
    val generated = KafkaConsumer.generateCode(form)

    models.TestHelper.assertEqualsFile(
      "/kafka-consumer.txt",
      generated(0).contents
    )
  }

}
