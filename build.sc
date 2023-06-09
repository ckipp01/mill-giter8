import $ivy.`com.goyeau::mill-scalafix::0.2.11`
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.7.1`
import $ivy.`io.chris-kipp::mill-ci-release::0.1.9`

import mill._
import mill.scalalib._
import mill.scalalib.scalafmt._
import mill.scalalib.publish._
import mill.scalalib.api.ZincWorkerUtil
import com.goyeau.mill.scalafix.ScalafixModule
import mill.scalalib.api.Util.scalaNativeBinaryVersion
import de.tobiasroeser.mill.integrationtest._
import io.kipp.mill.ci.release.CiReleaseModule
import io.kipp.mill.ci.release.SonatypeHost

val millVersions = Seq("0.10.12", "0.11.0-M10")
val millBinaryVersions = millVersions.map(scalaNativeBinaryVersion)
val scala213 = "2.13.10"
val pluginName = "mill-giter8"

def millBinaryVersion(millVersion: String) = scalaNativeBinaryVersion(
  millVersion
)

def millVersion(binaryVersion: String) =
  millVersions.find(v => millBinaryVersion(v) == binaryVersion).get

object plugin extends Cross[Plugin](millBinaryVersions: _*)
class Plugin(millBinaryVersion: String)
    extends ScalaModule
    with CiReleaseModule
    with ScalafixModule
    with ScalafmtModule {

  override def scalaVersion = scala213

  override def millSourcePath = super.millSourcePath / os.up

  override def artifactName =
    s"${pluginName}_mill${millBinaryVersion}"

  override def pomSettings = PomSettings(
    description = "Giter8 plugin for Mill",
    organization = "io.chris-kipp",
    url = "https://github.com/ckipp01/mill-giter8",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl
      .github(owner = "ckipp01", repo = "mill-giter8"),
    developers =
      Seq(Developer("ckipp01", "Chris Kipp", "https://github.com/ckipp01"))
  )

  override def sonatypeHost: Option[SonatypeHost] = Some(SonatypeHost.s01)

  override def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    ivy"com.lihaoyi::mill-scalalib:${millVersion(millBinaryVersion)}"
  )

  override def ivyDeps = Agg(
    ivy"org.foundweekends.giter8::giter8-lib:0.16.2",
    ivy"com.lihaoyi::requests:0.8.0"
  )

  override def scalacOptions = Seq("-Ywarn-unused", "-deprecation")

  override def scalafixScalaBinaryVersion =
    ZincWorkerUtil.scalaBinaryVersion(scala213)

  override def scalafixIvyDeps = Agg(
    ivy"com.github.liancheng::organize-imports:0.6.0"
  )
}

object itest extends Cross[ItestCross](millVersions: _*)
class ItestCross(millVersion: String) extends MillIntegrationTestModule {

  override def millSourcePath = super.millSourcePath / os.up

  override def millTestVersion = millVersion

  override def pluginsUnderTest = Seq(plugin(millBinaryVersion(millVersion)))

  override def perTestResources = T.sources { millSourcePath / "shared" }

  def testBase = millSourcePath / "src"

  override def testInvocations: T[Seq[(PathRef, Seq[TestInvocation.Targets])]] =
    T {
      Seq(
        PathRef(testBase / "minimal") -> Seq(
          TestInvocation.Targets(Seq("g8.validate"), noServer = true),
          TestInvocation
            .Targets(Seq("validatePackageStructure"), noServer = true)
        ),
        PathRef(testBase / "no-mill") -> Seq(
          TestInvocation.Targets(Seq("g8.validate"), noServer = true),
          TestInvocation.Targets(
            Seq("validatePackageStructure"),
            noServer = true
          )
        )
      )
    }
}
