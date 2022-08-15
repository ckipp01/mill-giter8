import $ivy.`com.goyeau::mill-scalafix::0.2.10`
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.6.1`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.2.0`

import mill._
import mill.scalalib._
import mill.scalalib.scalafmt._
import mill.scalalib.publish._
import mill.scalalib.api.ZincWorkerUtil
import com.goyeau.mill.scalafix.ScalafixModule
import mill.scalalib.api.Util.scalaNativeBinaryVersion
import de.tobiasroeser.mill.integrationtest._
import de.tobiasroeser.mill.vcs.version.VcsVersion

val millVersion = "0.10.0"
val scala213 = "2.13.8"
val pluginName = "mill-giter8"

def millBinaryVersion(millVersion: String) = scalaNativeBinaryVersion(
  millVersion
)

object plugin
    extends ScalaModule
    with PublishModule
    with ScalafixModule
    with ScalafmtModule {

  override def scalaVersion = scala213

  override def publishVersion = VcsVersion.vcsState().format()

  override def artifactName =
    s"${pluginName}_mill${millBinaryVersion(millVersion)}"

  override def pomSettings = PomSettings(
    description = "Mill plugin generated with mill-plugin.g8",
    organization = "io.chris-kipp",
    url = "https://github.com/ckipp01/mill-giter8",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl
      .github(owner = "ckipp01", repo = "mill-giter8"),
    developers =
      Seq(Developer("ckipp01", "Chris Kipp", "https://github.com/ckipp01"))
  )

  override def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  )

  override def ivyDeps = Agg(
    ivy"org.foundweekends.giter8::giter8-lib:0.14.0"
  )

  override def scalacOptions = Seq("-Ywarn-unused", "-deprecation")

  override def scalafixScalaBinaryVersion =
    ZincWorkerUtil.scalaBinaryVersion(scala213)

  override def scalafixIvyDeps = Agg(
    ivy"com.github.liancheng::organize-imports:0.6.0"
  )
}

object itest extends MillIntegrationTestModule {

  override def millTestVersion = millVersion

  override def pluginsUnderTest = Seq(plugin)

  def testBase = millSourcePath / "src"

  override def testInvocations: T[Seq[(PathRef, Seq[TestInvocation.Targets])]] =
    T {
      Seq(
        PathRef(testBase / "minimal") -> Seq(
          TestInvocation.Targets(Seq("g8.validate"), noServer = true)
        )
      )
    }
}
