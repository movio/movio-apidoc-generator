package scala.models

import com.bryzek.apidoc.generator.v0.models.{ File, InvocationForm }
import com.bryzek.apidoc.spec.v0.models.Attribute
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ ScalaEnums, ScalaCaseClasses, ScalaService, ScalaResource, ScalaOperation, ScalaUtil }
import scala.generator.ScalaDatatype.Container
import generator.ServiceFileNames
import play.api.libs.json.JsString

object PlaySystemTests extends PlaySystemTests
trait PlaySystemTests extends CodeGenerator {
  import KafkaUtil._
  import CaseClassUtil._

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
      case false ⇒ ""
      case true  ⇒ ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val configPath = ssd.namespaces.base.split("\\.").toSeq.dropRight(1).mkString(".")

    ssd.resources.map { resource: ScalaResource ⇒
      val resourceName = resource.plural
      val testName = resource.plural + "Test"

      val tests = resource.operations.map { operation: ScalaOperation ⇒
        generateTest(operation)
      }.mkString("\n")

      // Putting in default models
      val usedModels = resource.operations.flatMap { operation: ScalaOperation ⇒
        ssd.models.filter(_.qualifiedName == operation.resultType)
      }
      val usedModelsString = Seq(1, 2).flatMap(i ⇒
        usedModels.map(x ⇒
          s"""val ${getInstanceName(x, i)} = ${generateInstance(x, i, ssd).indent(4)}""")).mkString("\n")
      val usedModelsSeq = usedModels.map(m ⇒
        s"val ${getInstanceBatchName(m)} = Seq(${getInstanceName(m, 1)}, ${getInstanceName(m, 2)})").mkString("\n")

      val source = s"""$header
package services

import com.typesafe.config. { ConfigFactory, Config }
import org.scalatestplus.play.OneServerPerSuite
import play.api.test._
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.util.{ Try, Success }
import scala.concurrent.duration._
import scala.language.postfixOps

import movio.testtools.MovioSpec
import movio.testtools.kafka.{ KafkaTestKit, KafkaTestKitUtils }

class ${resourceName}SystemTest extends MovioSpec with KafkaTestKit with OneServerPerSuite with FutureAwaits with DefaultAwaitTimeout {
  import ${ssd.namespaces.base}._
  import ${ssd.namespaces.models}._
  import ${ssd.namespaces.base}.kafka._

  val kafkaServer = createKafkaServer()
  kafkaServer.startup()

  override def afterAll() = {
    kafkaServer.shutdown()
    zkServer.stop()
  }

  describe("${resourceName}") {
    ${tests.indent(4)}
  }

  ${usedModelsString}
  ${usedModelsSeq}

  lazy val brokerConnectionString = kafkaServer.config.hostName + ":" + kafkaServer.config.port
  lazy val tenant = "test"

  lazy val testConfig = ConfigFactory.parseString(s\"\"\"
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


  import scala.collection.JavaConversions._
  override lazy val app: FakeApplication = new FakeApplication(
    additionalConfiguration = testConfig.entrySet.map(v => v.getKey -> v.getValue.unwrapped).toMap
  )
}
"""
      File(testName + ".scala", Some("services"), source)
    }
  }

  def generateTest(operation: ScalaOperation): String = {
    val method = operation.method.toString.toLowerCase

    // Only create KafkaTests if result type is model - FIXME for other stuff
    operation.body match {
      case None ⇒ ""
      case Some(body) ⇒
        val model = operation.ssd.models.filter(body.name contains _.qualifiedName.toString).head

        val isBatch = operation.body.map(_.datatype match {
          case _: Container ⇒ true
          case _            ⇒ false
        }).getOrElse(false)

        val singleBatch = if (isBatch) "Batch" else "Single"
        val testName = s"${method.toUpperCase} ${model.name} $singleBatch"

        val batchSize = if (isBatch) 100 else 1
        val input = if (isBatch) getInstanceBatchName(model) else getInstanceName(model, 1)
        val result = if (isBatch) input + ".size" else input
        val consumerClassName = getConsumerClassName(model)
        val kafkaClass = getKafkaClass(model, operation.ssd).get
        val resourcePath = snakeToCamelCase(camelCaseToUnderscore(operation.resource.plural).toLowerCase)
        val functionName = operation.name
        val dataKey = getPayloadFieldName(kafkaClass)
        val expectedResult = if (isBatch)
          s"kafkaResult.map(_.${dataKey}) shouldBe $input"
        else
          s"kafkaResult.map(_.${dataKey}).head shouldBe result"
        s"""
it("${testName}") {
  val consumer = new ${consumerClassName}(testConfig, "consumer-group")
  val client = new Client(apiUrl = s"http://localhost:$$port")
  val promise = client.${resourcePath}.${functionName}(tenant, ${input})
  val result = await(promise)
  result shouldBe ${result}

  def processor(messages: Map[String, Seq[${kafkaClass.name}]]): Try[Map[String, Seq[${kafkaClass.name}]]] = Success(messages)
  awaitCondition("Message should be on the queue", interval = 500 millis) {
    val kafkaResult = consumer.processBatchThenCommit(processor, ${batchSize}).get(tenant)
    ${expectedResult}
  }
  consumer.shutdown
}"""
    }
  }
}
