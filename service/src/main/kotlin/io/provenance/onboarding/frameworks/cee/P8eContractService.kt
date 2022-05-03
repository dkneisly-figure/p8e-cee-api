package io.provenance.onboarding.frameworks.cee

import com.google.protobuf.Message
import cosmos.base.abci.v1beta1.Abci
import io.provenance.onboarding.domain.cee.ContractService
import io.provenance.onboarding.frameworks.provenance.SingleTx
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.loan.LoanScopeSpecification
import io.provenance.scope.sdk.Client
import io.provenance.scope.sdk.Session
import io.provenance.scope.sdk.SignedResult
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class P8eContractService : ContractService {
    private val log = KotlinLogging.logger { }

    override fun getContract(contractName: String): Class<out P8eContract> {
        return Class.forName(contractName).asSubclass(P8eContract::class.java)
    }

    override fun <T : P8eContract> setupContract(
        client: Client,
        contractClass: Class<T>,
        records: Map<String, Message>,
        scopeUuid: UUID,
        sessionUuid: UUID?
    ): Session =
        client
            .newSession(contractClass, LoanScopeSpecification::class.java)
            .setScopeUuid(scopeUuid)
            .configureSession(records, sessionUuid)
            .also { session ->
                log.info("[L: ${session.scopeUuid}, S: ${session.sessionUuid}] ${contractClass.simpleName} has been setup.")
            }

    override fun executeContract(client: Client, session: Session, executeTransaction: (SingleTx) -> Abci.TxResponse): Result<Abci.TxResponse> =
        runCatching {
            when (val result = client.execute(session)) {
                is SignedResult -> executeTransaction(SingleTx(result))
                else -> throw IllegalStateException("Must be a signed result since this is a single party contract.")
            }
        }

    private fun Session.Builder.configureSession(records: Map<String, Message>, sessionUuid: UUID? = null): Session =
        this.setSessionUuid(sessionUuid ?: UUID.randomUUID())
            .also { records.forEach { record -> it.addProposedRecord(record.key, record.value) } }
            .build()
}
