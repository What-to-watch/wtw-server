package services

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

//sealed trait Cursor {
//  def decode(encoded: String): Cursor
//}
//case class IdCursor(id: Int) extends Cursor {
//  override def decode(encoded: String): Cursor = ???
//}

object CursorParseError extends Throwable

case class Cursor(field: Option[String], id: String)
object Cursor {

  val NULL_FIELD = "null"
  val MINIMUM_BUDGET_INT = "-1"
  val MAXIMUM_BUDGET_INT = "380000000"
  val MAX_DATE = "2099-12-31"
  val MIN_DATE = "1900-01-01"

  def decode(value: String): Cursor = {
    val bytes = Base64.getDecoder.decode(value.getBytes(UTF_8))
    val parsed = new String(bytes).split("_")
    val field = if(parsed(0) == NULL_FIELD) None else Some(parsed(0))
    new Cursor(field, parsed(1))
  }

  def encode(field: Option[String], id: String): String = {
    val value = s"${field.fold({NULL_FIELD})(identity)}_$id"
    new String(Base64.getEncoder.encode(value.getBytes(UTF_8)))
  }
}

