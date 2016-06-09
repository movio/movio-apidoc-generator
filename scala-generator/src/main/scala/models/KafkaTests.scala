package scala.models

import com.bryzek.apidoc.generator.v0.models.{ File, InvocationForm }
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ ScalaEnums, ScalaCaseClasses, ScalaService, ScalaModel, ScalaField }
import generator.ServiceFileNames
import lib.Datatype._
import play.api.libs.json.JsString

object KafkaTests extends CodeGenerator {
  import CaseClassUtil._
  import KafkaUtil._

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
    val play2Json = Play2JsonExtended(ssd).generate()

    val header = addHeader match {
      case false ⇒ ""
      case true  ⇒ ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val kafkaModels = getKafkaModels(ssd)

    // Return list of files
    kafkaModels.map { model ⇒
      val className = model.name
      val configPath = ssd.namespaces.base.split("\\.").toSeq.dropRight(1).mkString(".")
      val source = s""" $header
package ${ssd.namespaces.base}.kafka

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
          def processor(messages: Map[String, Seq[${className}]]): scala.util.Try[Map[String, Seq[${className}]]] =  scala.util.Success(messages)
          consumer.processBatchThenCommit(processor) shouldBe scala.util.Success(Map.empty)
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
          def processor(messages: Map[String, Seq[${className}]]): scala.util.Try[Map[String, Seq[${className}]]] = {
            println(messages)
            println("do some side effecting stuff here")
            scala.util.Success(messages)
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
          def processor(messages: Map[String, Seq[${className}]]): scala.util.Try[Map[String, Seq[${className}]]] =  {
            println(messages)
            println("do some side effecting stuff here")
            scala.util.Success(messages)
          }
          // Use distinct because there are items in the queue from other tests
          consumer.processBatchThenCommit(processor, 100).get(tenant) shouldBe entities
        }

        consumer.shutdown
      }
    }
    
    it("consumer ignores null payload messages, to support deletes on topics with compaction") {
      new Fixture {
        val topic = ${className}Topic.topic(tenant)
        val rawProducer = createKeyedProducer[String, String](topic, kafkaServer)(k ⇒ k, m ⇒ m)

        producer.sendWrapped(entity1, tenant)
        // Produce null payload message
        rawProducer.send("anId", null)
        producer.sendWrapped(entity2, tenant)

        // And consume them
        var consumedEntities = Seq.empty[KafkaPerson]
        awaitCondition("All messages should get processed") {
          def processor(messages: Map[String, Seq[${className}]]): scala.util.Try[Map[String, Seq[${className}]]] =  {
            println(messages)
            println("do some side effecting stuff here")
            scala.util.Success(messages)
          }
          
          // Use distinct because there are items in the queue from other tests
          consumer.processBatchThenCommit(processor, 100).get.get(tenant).foreach { messages =>
            consumedEntities ++= messages
          }
          
          consumedEntities shouldBe Seq(entity1, entity2)
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
      |  producer {
      |    broker-connection-string : "$$brokerConnectionString"
      |  }
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
      |\"\"\".stripMargin)
      .withFallback(ConfigFactory.load())

    val producer = new ${className}Producer(testConfig)
    val consumer = new ${className}Consumer(testConfig, new java.util.Random().nextInt.toString)
  }

  val entity1 = ${generateInstance(model, 1, ssd).indent(4)}
  val entity2 = ${generateInstance(model, 2, ssd).indent(4)}
}
"""
      ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, s"${className}Test", source, Some("Scala"))
    }
  }

}
