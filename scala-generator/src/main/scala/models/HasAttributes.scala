package scala.models

import scala.language.implicitConversions
import simulacrum._

import com.bryzek.apidoc.spec.v0.models._

@typeclass trait HasAttributes[T] {

  def getAttributes(t: T): Seq[Attribute]

  def findAttribute(t: T, name: String): Option[Attribute] =
    getAttributes(t).find(_.name == name)
}

object HasAttributesI {
  implicit val modelHasAttributes = new HasAttributes[Model] {
    def getAttributes(t: Model) = t.attributes
  }
  implicit val fieldHasAttributes = new HasAttributes[Field] {
    def getAttributes(t: Field) = t.attributes
  }
  implicit val bodyHasAttributes = new HasAttributes[Body] {
    def getAttributes(t: Body) = t.attributes
  }
  implicit val enumHasAttributes = new HasAttributes[Enum] {
    def getAttributes(t: Enum) = t.attributes
  }
  implicit val enumValueHasAttributes = new HasAttributes[EnumValue] {
    def getAttributes(t: EnumValue) = t.attributes
  }
  implicit val headerHasAttributes = new HasAttributes[Header] {
    def getAttributes(t: Header) = t.attributes
  }
  implicit val operationHasAttributes = new HasAttributes[Operation] {
    def getAttributes(t: Operation) = t.attributes
  }
  implicit val parameterHasAttributes = new HasAttributes[Parameter] {
    def getAttributes(t: Parameter) = t.attributes
  }
  implicit val resourceHasAttributes = new HasAttributes[Resource] {
    def getAttributes(t: Resource) = t.attributes
  }
  implicit val responseHasAttributes = new HasAttributes[Response] {
    def getAttributes(t: Response) = t.attributes
  }
  implicit val unionHasAttributes = new HasAttributes[Union] {
    def getAttributes(t: Union) = t.attributes
  }
  implicit val unionTypeHasAttributes = new HasAttributes[UnionType] {
    def getAttributes(t: UnionType) = t.attributes
  }
}
