package scala.models

import scala.language.implicitConversions
import simulacrum._

import com.bryzek.apidoc.spec.v0.models._

import scala.generator._
import lib.generator.GeneratorUtil
import lib._


@typeclass trait HasClassName[T] {

  def className(t: T, service: Service): String =
    ScalaUtil.toClassName(getName(t, service))

  def qualifiedName(t: T, service: Service): String =
    Namespaces(service.namespace).models + "." + className(t, service)

  def getName(t: T, service: Service): String
}

object HasClassNames {

  implicit val classNamesModel = new HasClassName[Model] {
    def getName(t: Model, service: Service) = t.name
  }

  implicit val classNamesUnion = new HasClassName[Union] {
    def getName(t: Union, service: Service) = t.name
  }

  //FIXME - test
  implicit val classNamesUnionType = new HasClassName[UnionType] {

    def getName(t: UnionType, service: Service) = t.`type`

    override def className(t: UnionType, service: Service): String =
      ScalaUtil.quoteNameIfKeyword(lib.Text.snakeToCamelCase(getName(t, service)))
  }

  //FIXME - test
  implicit val classNamesBody = new HasClassName[Body] {
    def getName(t: Body, service: Service): String = {
      val ssd = ScalaService(service)
      val datatypeResolver = GeneratorUtil.datatypeResolver(service)
      val tt: Datatype = datatypeResolver.parse(t.`type`, true).getOrElse {
        sys.error(ssd.errorParsingType(t.`type`, s"body[$t]"))
      }
      ssd.scalaDatatype(tt).name
    }
  }

}
