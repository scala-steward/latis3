package latis.server

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.module.catseffect.syntax.*

import latis.catalog.FdmlCatalog
import latis.ops.OperationRegistry
import latis.server.Latis3ServerBuilder.*

object Latis3Server extends IOApp {

  private val loader: ServiceInterfaceLoader =
    new ServiceInterfaceLoader()

  private val getServiceConf: IO[ServiceConf] =
    latisConfigSource.loadF[IO, ServiceConf]()

  private val operationRegistry: OperationRegistry =
    OperationRegistry.default

  def run(args: List[String]): IO[ExitCode] =
    (for {
      logger      <- Resource.eval(Slf4jLogger.create[IO])
      serverConf  <- Resource.eval(getServerConf)
      catalogConf <- Resource.eval(getCatalogConf)
      catalog     <- Resource.eval(
        FdmlCatalog.fromDirectory(catalogConf.dir, catalogConf.validate, operationRegistry)
      )
      serviceConf <- Resource.eval(getServiceConf)
      interfaces  <- Resource.eval(loader.loadServices(serviceConf, catalog, operationRegistry))
      server      <- mkServer(serverConf, defaultLandingPage, interfaces, logger)
    } yield server)
      .use(_ => IO.never)
      .as(ExitCode.Success)
}
