import $exec.plugins
import io.kipp.mill.giter8.G8Module
import $ivy.`org.scalameta::munit:0.7.29`
import munit.Assertions._

object g8 extends G8Module {
  override def validationTargets = Seq("__.compile")
}
