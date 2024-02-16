package net.opendasharchive.openarchive.di

import net.opendasharchive.openarchive.data.repository.GDriveFileDataRepository
import net.opendasharchive.openarchive.domain.repository.GDriveFileRepository
import org.koin.dsl.module

val dataModule = module {
    factory<GDriveFileRepository> { GDriveFileDataRepository() }
}
