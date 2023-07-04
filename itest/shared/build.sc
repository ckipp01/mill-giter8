import $file.plugins
import mill._
import io.kipp.mill.giter8.G8Module
import $ivy.`org.scalameta::munit:0.7.29`
import munit.Assertions._

object g8 extends G8Module {
  override def validationTargets = Seq("__.compile")
}

/** Just tests to ensure that $package$ is actually correctly expanded and the
  * strucutre looks as it should
  */
def validatePackageStructure = T {
  val expectedFile =
    T.workspace / "out" / "g8" / "generate.overridden" / "io" / "kipp" / "mill" / "giter8" / "G8Module" / "generate.dest" / "result" / "minimal" / "src" / "com" / "example" / "someproject" / "Main.scala"

  // Location in new mill. So instead of coding fancy logic of duplicating anything we'll just check both
  val newExpect =
    T.workspace / "out" / "g8" / "generate.super" / "io" / "kipp" / "mill" / "giter8" / "G8Module" / "generate.dest" / "result" / "minimal" / "src" / "com" / "example" / "someproject" / "Main.scala"

  assert(os.exists(expectedFile) || os.exists(newExpect))
}
