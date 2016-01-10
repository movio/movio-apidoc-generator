package scala.models

import com.bryzek.apidoc.generator.v0.models.InvocationForm
import org.scalatest.{ ShouldMatchers, FunSpec }
import scala.generator.ScalaService

class PlayApplicationModuleSpec extends FunSpec with ShouldMatchers {

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
          "attributes": [],
          "plural": "kafka_members",
          "fields": [
            { "name": "v0", "type": "member", "required": true, "attributes": [] }
          ],
          "attributes": [
            { "name": "kafka_props",
              "value": {
                "data_type": "member",
                "message_key": "v0.id",
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
package modules

import com.google.inject.AbstractModule
import com.typesafe.config.Config
import play.api.{ Configuration, Environment }

class ApplicationModule(
    environment: Environment,
    configuration: Configuration
) extends AbstractModule {

  def configure() = {
    import services._
    bind(classOf[Config]).toInstance(configuration.underlying)

    bind(classOf[MembersService])
  }
}"""

  it("generates kafka publish test") {
    lazy val form = InvocationForm(service)
    PlayApplicationModule.generateCode(form).head.contents shouldBe expected
  }

}
