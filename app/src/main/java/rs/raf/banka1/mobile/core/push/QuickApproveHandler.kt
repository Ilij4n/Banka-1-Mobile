package rs.raf.banka1.mobile.core.push

import rs.raf.banka1.mobile.data.apis.VerificationApi
import rs.raf.banka1.mobile.data.local.VerificationCodeDao
import rs.raf.banka1.mobile.data.remote.NetworkResult
import rs.raf.banka1.mobile.data.remote.requests.ValidateRequest
import javax.inject.Inject
import javax.inject.Singleton

sealed class ApproveResult {
    object Verified : ApproveResult()
    data class Failed(val reason: String) : ApproveResult()
}

@Singleton
class QuickApproveHandler @Inject constructor(
    private val verificationApi: VerificationApi,
    private val verificationCodeDao: VerificationCodeDao
) {
    suspend fun approve(sessionId: Long, code: String): ApproveResult {
        return when (val result = verificationApi.validate(ValidateRequest(sessionId, code))) {
            is NetworkResult.Success -> {
                val response = result.data
                if (response.status == "VERIFIED") {
                    verificationCodeDao.markUsedBySessionId(sessionId.toString())
                    ApproveResult.Verified
                } else {
                    ApproveResult.Failed(
                        when (response.status) {
                            "EXPIRED" -> "Kod je istekao"
                            "CANCELLED" -> "Odobravanje odbijeno"
                            else -> "Pogresan kod. Preostalo: ${response.remainingAttempts}"
                        }
                    )
                }
            }
            is NetworkResult.Error -> ApproveResult.Failed("Odobravanje nije uspelo")
            is NetworkResult.Exception -> ApproveResult.Failed("Odobravanje nije uspelo")
            is NetworkResult.Ignored -> ApproveResult.Failed("Odobravanje nije uspelo")
        }
    }
}
