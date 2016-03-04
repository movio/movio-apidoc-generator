package scala.models

import com.bryzek.apidoc.generator.v0.models.InvocationForm
import org.scalatest.{ ShouldMatchers, FunSpec }
import scala.generator.ScalaService

class PlaySystemTestsSpec extends FunSpec with ShouldMatchers {

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
          "attributes": [],
          "plural": "kafka_members",
          "fields": [
            { "name": "v0", "type": "member", "required": true, "attributes": [] }
          ],
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

  val expected = s"""
it("POST Member Single") {
  val consumer = new KafkaMemberConsumer(testConfig, "consumer-group")
  val client = new Client(apiUrl = s"http://localhost:$$port")
  val promise = client.members.postMember(tenant, memberEntity1)
  val result = await(promise)
  result shouldBe memberEntity1

  def processor(messages: Map[String, Seq[KafkaMember]]): scala.util.Try[Map[String, Seq[KafkaMember]]] = scala.util.Success(messages)
  awaitCondition("Message should be on the queue", interval = 500 millis) {
    val kafkaResult = consumer.processBatchThenCommit(processor, 1).get(tenant)
    kafkaResult.map(_.v0).head shouldBe result
  }
  consumer.shutdown
}"""

  it("generates kafka publish test") {
    val operation = ssd.resources.head.operations.head
    PlaySystemTests.generateTest(operation, service) shouldBe expected
  }

}
