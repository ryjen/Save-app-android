package net.opendasharchive.openarchive.domain.repository

interface FileRepository {
    suspend fun upload(): Boolean
    suspend fun createFolder(url: String)
}
