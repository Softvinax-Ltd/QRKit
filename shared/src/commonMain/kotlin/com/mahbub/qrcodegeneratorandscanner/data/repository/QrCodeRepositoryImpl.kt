package com.mahbub.qrcodegeneratorandscanner.data.repository

import com.mahbub.qrcodegeneratorandscanner.data.mapper.toDomain
import com.mahbub.qrcodegeneratorandscanner.data.mapper.toLibrary
import com.mahbub.qrcodegeneratorandscanner.domain.model.GeneratedQr
import com.mahbub.qrcodegeneratorandscanner.domain.model.QrErrorLevel
import com.mahbub.qrcodegeneratorandscanner.domain.repository.QrCodeRepository
import com.mahbub.qrkit.QrCodeGenerator
import com.mahbub.qrkit.QrCodeOptions

class QrCodeRepositoryImpl : QrCodeRepository {
    override fun generate(
        data: String,
        errorLevel: QrErrorLevel,
        version: Int,
        mask: Int,
    ): Result<GeneratedQr> = runCatching {
        QrCodeGenerator.generate(
            data = data,
            options = QrCodeOptions(
                errorCorrection = errorLevel.toLibrary(),
                version = version,
                mask = mask,
            ),
        ).toDomain(data)
    }
}
