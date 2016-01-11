package scala.models

import scala.generator._
import org.scalatest.{ ShouldMatchers, FunSpec }

class KafkaUtilSpec extends FunSpec with ShouldMatchers {
  import KafkaUtil._
  import CaseClassUtil._

  val json = models.TestHelper.buildJson("""
      "imports": [],
      "headers": [],
      "info": [],
      "enums": [],
      "unions": [],
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
                "topic": "s\"master-movie-${tenant}\""
              }
            }
          ]
        }
      ],
      "resources": [
        {
          "type": "member",
          "plural": "members",
          "description": null,
          "attributes": [],
          "operations": [
            {
              "method": "POST",
              "attributes": [],
              "path": "/member",
              "parameters": [],
              "description": null,
              "body": {
                "type": "member",
                "attributes": []
              },
              "responses": [
                {
                  "code": { "integer": { "value": 200 } },
                  "attributes": [],
                  "type": "member"
                }
              ]
            }
          ]
        }
      ]
  """)

  lazy val service = models.TestHelper.service(json)
  lazy val ssd = ScalaService(service)

  it("is a kafka class") {
    isKafkaClass(getModelByName("member", ssd).get) shouldBe false
    isKafkaClass(getModelByName("kafka_member", ssd).get) shouldBe true
  }

  it("get kafka models") {
    val result = getKafkaModels(ssd)
    result.size shouldBe 1
    result.head.originalName shouldBe "kafka_member"
  }

  it("get kafka wrapper classes") {
    val payload = getModelByName("member", ssd).get
    val wrapper = getModelByName("kafka_member", ssd).get
    getKafkaClass(payload, ssd).get.name shouldBe wrapper.name
  }

  it("get kafka consumer class") {
    val payload = getModelByName("member", ssd).get
    getConsumerClassName(payload) shouldBe "KafkaMemberConsumer"
  }

}
