package scala.models

import com.bryzek.apidoc.generator.v0.models.{ File, InvocationForm }
import com.bryzek.apidoc.spec.v0.models.Attribute
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ ScalaEnums, ScalaCaseClasses, ScalaService, ScalaResource, ScalaOperation, ScalaUtil }
import scala.generator.ScalaDatatype.Container
import generator.ServiceFileNames
import scala.generator.MovioCaseClasses
import play.api.libs.json.JsString

object PlaySystemTests extends PlaySystemTests
trait PlaySystemTests extends CodeGenerator {

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

    val models = ssd.models.filter(_.attribute(MovioCaseClasses.KafkaClassKey).isDefined)

    ssd.resources.map { resource: ScalaResource ⇒
      val resourceName = resource.plural
      val testName = resource.plural + "Test"

      // Find KafkaProducer that contians the model for $resourceName
      val kafkaProducers = models.flatMap(_.model.attributes.map(attr ⇒ {
        (attr.value \ "data_type").as[JsString].value
      }))
      val resourceBodies = resource.operations.flatMap(_.body.map(_.body.`type`))

      val producerMap = kafkaProducers.intersect(resourceBodies).map(t ⇒ t → ScalaUtil.toClassName(t)).toMap

      val producers = producerMap.values.map(p ⇒ s"val kafka${p}Producer = new Kafka${p}Producer(config)").mkString("\n")

      val tests = resource.operations.map { operation: ScalaOperation ⇒
        // Only create KafkaTests if result type is model - FIXME for other stuff
        operation.body match {
          case None ⇒ ""
          case Some(body) ⇒
            val model = ssd.models.filter(body.name contains _.qualifiedName.toString).head
            val kafkaModel = models.filter(m => m.attribute(model.name).isDefined).headOption
            val kk = model.getKafkaModelAttribute.map(_.dataType)
            println(kk) 

            val method = operation.method.toString.toLowerCase
            val parameters = operation.parameters

            val bodyType = operation.body.map(_.name).getOrElse("Unit")

            val firstParamName = parameters.map(_.name).headOption.getOrElse("")

            val dataArg = bodyType match {
              case "Unit" ⇒ None
              case _      ⇒ Some(s"""data: ${bodyType}""")
            }
            val additionalArgs = Seq(Some("request: Request[T]"), dataArg).flatten
            val argList = ScalaUtil.fieldsToArgList(additionalArgs ++ (parameters.map(_.definition()))).mkString(", ")

            val argNameList = (Seq("request.body", "request") ++ operation.parameters.map(_.name)).mkString(", ")

            val producerName = operation.body.map(_.body.`type`)
              .map(_.replaceAll("[\\[\\]]", ""))
              .map(clazz ⇒ s"kafka${ScalaUtil.toClassName(clazz)}Producer")
              .getOrElse("???")

            // Use in service
            val resultType = operation.resultType

            // val model = ssd.models.filter(_.qualifiedName == operation.resultType).headOption
            val bodyScala = method match {
              case "post" | "put" ⇒ s"""${producerName}.send(data, ${firstParamName})"""
              case "get" ⇒
                // FIXME will break with Int, String etc
                // Create a default Case Class
                val caseClass = KafkaTests.createEntity(model, 1, models)
                s"Try { ${caseClass.indent(6)} }"
              case _ ⇒ "???"
            }

            val resourcePath = snakeToCamelCase(camelCaseToUnderscore(resource.plural).toLowerCase)

            val kafkaClass = "???"
            val isBatch = operation.body.map(_.datatype match {
              case _: Container ⇒ true
              case _            ⇒ false
            }).getOrElse(false)

            val name = if (isBatch) "Batch" else "Single"
            val batchSize = if (isBatch) 100 else 1
            val inputAndResult = if (isBatch) s"a${model.name}Entities" else s"a${model.name}Entity1"
            val results = if (isBatch) s"a${model.name}Entities" else s"Seq(a${model.name}Entity1)"

            s"""
it("${method.toUpperCase} ${name}") {
  val consumer = new ${kafkaClass}Consumer(testConfig, "consumer-group")
  val client = new Client(apiUrl = s"http://localhost:$$port")
  val result = client.${resourcePath}.${operation.name}(tenant, ${inputAndResult})
  await(result) shouldBe ${inputAndResult}

  awaitCondition("Message should be on the queue") {
    def processor(messages: Map[String, Seq[${kafkaClass}]]): Try[Map[String, Seq[${kafkaClass}]]] = Success(messages)
    val kafkaResult = consumer.processBatchThenCommit(processor, ${batchSize}).get(tenant)
    kafkaResult.size shouldBe result.size}
    kafkaResult.map(_.data) shouldBe result
  }
  consumer.shutdown
}
"""
        }
      }.mkString("\n")

      // Putting in default models
      val usedModels = resource.operations.flatMap { operation: ScalaOperation ⇒
        ssd.models.filter(_.qualifiedName == operation.resultType)
      }
      val usedModelsString = Seq(1, 2).flatMap(i ⇒
        usedModels.map(x ⇒
          s"""val a${x.name}Entity${i} = ${KafkaTests.createEntity(x, i, ssd.models).indent(4)}""")).mkString("\n")
      val usedModelsSeq = usedModels.map(m ⇒
        s"val a${m.name}Entities = Seq(a${m.name}Entity1, a${m.name}Entity2)").mkString("\n")

      val source = s"""$header
package services

import com.typesafe.config. { ConfigFactory, Config }
import org.scalatestplus.play.OneServerPerSuite
import play.api.test._
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.util.{ Try, Success }

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
      |movio.cinema.movie.core.all.kafka {
      |  consumer {
      |    offset-storage-type = "kafka"
      |    offset-storage-dual-commit = false
      |    timeout.ms = "100"
      |    zookeeper.connection = "$${zkServer.getConnectString}"
      |  }
      |}
      |
      |movio.cinema.movie.core.all.kafka {
      |  producer {
      |    broker-connection-string : "$$brokerConnectionString"
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
}
