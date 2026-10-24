package ltd.softvinax.qrkit.app.di

import ltd.softvinax.qrkit.app.data.repository.QrCodeRepositoryImpl
import ltd.softvinax.qrkit.app.domain.repository.QrCodeRepository
import ltd.softvinax.qrkit.app.domain.usecase.GenerateQrCodeUseCase
import ltd.softvinax.qrkit.app.presentation.generate.GenerateViewModel
import ltd.softvinax.qrkit.app.presentation.scan.ScanViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<QrCodeRepository> { QrCodeRepositoryImpl() }
    factory { GenerateQrCodeUseCase(repository = get()) }

    viewModel { GenerateViewModel(generateQrCode = get()) }
    viewModel { ScanViewModel() }
}
