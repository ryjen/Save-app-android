package net.opendasharchive.openarchive.data.repository

import net.opendasharchive.openarchive.data.datasource.GDriveDataSource
import net.opendasharchive.openarchive.domain.repository.GDriveFileRepository

class GDriveFileDataRepository(
    dataSource: GDriveDataSource
) : GDriveFileRepository {
    override suspend fun upload(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun createFolder(url: String) {
        TODO("Not yet implemented")
    }
}
