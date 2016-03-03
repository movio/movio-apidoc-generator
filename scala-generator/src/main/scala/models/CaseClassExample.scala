package scala.models

import scala.language.implicitConversions
import simulacrum._

import com.bryzek.apidoc.spec.v0.models._

// @typeclass trait Field[F] {
//   def defaultValue(
//     field: F,
//     name: String,
//     minimum: Int = 0,
//     maximum: Int = 10): String = "???"

//   // Classes / Enums
//   def className(f: F): String

//   def originalName(f: F): String

// }


// object FieldTypeClassese {
//   implicit val string = new Field[String] {
//     def defaultValue(
//       field: String,
//       name: String,
//       min: Int = 0,
//       max: Int = Int.MaxValue
//     ) = field + "1"
//   }
// }


// @typeclass trait FieldValidation[F] {
//   def validation(
//     field: F,
//     name: String,
//     minimum: Int = 0,
//     maximum: Int = 10): Option[String]
// }

object FieldValidations {
  // implicit val string = new FieldValidation[lib.Datatype.String] {
  //   def validation(
  //     field: String,
  //     name: String,
  //     min: Int = 0,
  //     max: Int = Int.MaxValue
  //   ) = Some("validation")
  // }
}


