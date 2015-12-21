package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ScalaEnums, ScalaCaseClasses, ScalaService, ScalaModel, ScalaField}
import generator.ServiceFileNames
import scala.generator.MovioCaseClasses
import lib.Datatype._
import play.api.libs.json.JsString

object KafkaTests extends CodeGenerator {

  override def invoke(
    form: InvocationForm
  ): Either[Seq[String], Seq[File]] = {
    Right(generateCode(form))
  }

  def generateCode(
    form: InvocationForm,
    addHeader: Boolean = true
  ): Seq[File] = {
    val ssd = ScalaService(form.service)

    val prefix = underscoreAndDashToInitCap(ssd.name)
    val enumJson: String = ssd.enums.map { ScalaEnums(ssd, _).buildJson() }.mkString("\n\n")
    val play2Json = Play2Json(ssd).generate()

    val header = addHeader match {
      case false => ""
      case true => ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val models = ssd.models.filter(model =>
      model.model.attributes.filter(attr =>
        attr.name == MovioCaseClasses.KafkaClassKey
      ).size > 0
    )

    def createEntity(model: ScalaModel, number: Int, models: Seq[ScalaModel]): String = {
      def getModelForField(name: String): ScalaModel = {
        models.find(model => model.originalName == name).get
      }

      val fields = model.fields.map{field =>
        val defaultValue =
          field.field.attributes.find(_.name == MovioCaseClasses.ScalaTypeKey) match {
            case Some(attr) =>
              (attr.value \ MovioCaseClasses.ScalaExampleKey).toOption match {
                case Some(example) => example.as[JsString].value
                case None =>
                  (attr.value \ MovioCaseClasses.ScalaDefaultKey).toOption match {
                    case Some(default) => default.as[JsString].value
                    case None =>
                      if (field.required)
                        throw new RuntimeException("manditory fields must provide examples")
                      else
                        "None"
                  }
              }

            case None =>
              field.`type` match {
                case t: Primitive => t match {
                  case Primitive.Boolean => true
                  case Primitive.Double => 2.0 + number
                  case Primitive.Integer => 21 + number
                  case Primitive.Long => 101L + number
                  case Primitive.DateIso8601 => "new org.joda.time.Date()"
                  case Primitive.DateTimeIso8601 => "new org.joda.time.DateTime()"
                  case Primitive.Decimal => 1.31 + number
                  case Primitive.Object => ""
                  case Primitive.String => s""""${field.name}${number}""""
                  case Primitive.Unit => ""
                  case Primitive.Uuid => "new java.util.UUID()"
                  case _ => "???"
                }
                case t: Container => t match {
                  case Container.List(name) => "List.empty"
                  case Container.Map(name) => "Map.empty"
                  case Container.Option(name) => "None" 
                }
                case t: UserDefined => t match {
                  case UserDefined.Model(name) => createEntity(getModelForField(name), number, models).indent(2)
                  case UserDefined.Enum(name) => "" // TBC
                  case UserDefined.Union(name) => "" // TBC
                }
                case e => "???"
              }
          }

        s"""${field.name} = $defaultValue"""
      }.mkString("", ",\n", "").indent(2)
      s"""
${model.name} (
$fields
)
"""
    }

    // Return list of files
    models.map{ model =>
      val className = model.name
      val configPath = ssd.namespaces.base.split("\\.").toSeq.dropRight(1).mkString(".")
      val source = s""" $header
package ${ssd.namespaces.base}.kafka

import scala.util.Try
import scala.util.Success

import org.joda.time.LocalDateTime
import org.mockito.Matchers.any
import org.mockito.Matchers.{ eq ⇒ is }

import com.typesafe.config.ConfigFactory

import movio.testtools.MovioSpec
import movio.testtools.kafka.{ KafkaTestKit, KafkaTestKitUtils }


class ${className}Tests extends MovioSpec with KafkaTestKit {
  import ${ssd.namespaces.base}.models._

  val kafkaServer = createKafkaServer()
  kafkaServer.startup()

  override def afterAll() = {
    kafkaServer.shutdown()
    zkServer.stop()
  }

  describe("${className} Producer and Consumer") {
    it("should timeout with no messages") {
      new Fixture {
        awaitCondition("Message should get processed") {
          def processor(messages: Map[String, Seq[${className}]]): Try[Map[String, Seq[${className}]]] =  Success(messages)
          consumer.processBatchThenCommit(processor) shouldBe Success(Map.empty)
        }

        consumer.shutdown
      }
    }

    it("should send and receive a message") {
      new Fixture {
        // Produce test message
        producer.sendWrapped(entity1, tenant)

        // And consume it
        awaitCondition("Message should get processed") {
          def processor(messages: Map[String, Seq[${className}]]): Try[Map[String, Seq[${className}]]] = {
            println(messages)
            println("do some side effecting stuff here")
            Success(messages)
          }
          consumer.processBatchThenCommit(processor).get(tenant) shouldBe Seq(entity1)
        }

        consumer.shutdown
      }
    }

    it("should send and receive a batch of messages") {
      new Fixture {
        val entities = Seq(entity1, entity2)

        // Produce test message
        producer.sendWrapped(entities, tenant)

        // And consume it
        awaitCondition("Message should get processed") {
          def processor(messages: Map[String, Seq[${className}]]): Try[Map[String, Seq[${className}]]] =  {
            println(messages)
            println("do some side effecting stuff here")
            Success(messages)
          }
          // Use distinct because there are items in the queue from other tests
          consumer.processBatchThenCommit(processor, 100).get(tenant) shouldBe entities
        }

        consumer.shutdown
      }
    }
  }

  trait Fixture {

    val brokerConnectionString = kafkaServer.config.hostName + ":" + kafkaServer.config.port
    val tenant = KafkaTestKitUtils.tempTopic()

    val testConfig = ConfigFactory.parseString(s\"\"\"
      |configuration {
      |  log-on-startup = false
      |}
      |
      |${configPath}.kafka {
      |  consumer {
      |    offset-storage-type = "kafka"
      |    offset-storage-dual-commit = false
      |    timeout.ms = "100"
      |    zookeeper.connection = "$${zkServer.getConnectString}"
      |  }
      |}
      |
      |${configPath}.kafka {
      |  producer {
      |    broker-connection-string : "$$brokerConnectionString"
      |  }
      |}
      |\"\"\".stripMargin)
      .withFallback(ConfigFactory.load())

    val producer = new ${className}Producer(testConfig)
    val consumer = new ${className}Consumer(testConfig, tenant)
  }

  val entity1 = ${createEntity(model, 1, ssd.models).indent(4)}
  val entity2 = ${createEntity(model, 2, ssd.models).indent(4)}
}
"""
      ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, s"${className}Test", source, Some("Scala"))
    }
  }
}
