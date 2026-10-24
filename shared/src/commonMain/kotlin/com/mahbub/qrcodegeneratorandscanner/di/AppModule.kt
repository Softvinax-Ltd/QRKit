package com.mahbub.qrcodegeneratorandscanner.di

import com.mahbub.qrcodegeneratorandscanner.data.repository.QrCodeRepositoryImpl
import com.mahbub.qrcodegeneratorandscanner.domain.repository.QrCodeRepository
import com.mahbub.qrcodegeneratorandscanner.domain.usecase.GenerateQrCodeUseCase
import com.mahbub.qrcodegeneratorandscanner.presentation.generate.GenerateViewModel
import com.mahbub.qrcodegeneratorandscanner.presentation.scan.ScanViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<QrCodeRepository> { QrCodeRepositoryImpl() }
    factory { GenerateQrCodeUseCase(repository = get()) }

    viewModel { GenerateViewModel(generateQrCode = get()) }
    viewModel { ScanViewModel() }
}
