@file:JvmSynthetic

package com.walletconnect.push.wallet.engine

import com.walletconnect.android.impl.common.SDKError
import com.walletconnect.android.impl.common.model.ConnectionState
import com.walletconnect.android.impl.common.model.type.EngineEvent
import com.walletconnect.android.impl.utils.DAY_IN_SECONDS
import com.walletconnect.android.internal.common.crypto.KeyManagementRepository
import com.walletconnect.android.internal.common.exception.GenericException
import com.walletconnect.android.internal.common.exception.Uncategorized
import com.walletconnect.android.internal.common.model.IrnParams
import com.walletconnect.android.internal.common.model.JsonRpcInteractorInterface
import com.walletconnect.android.internal.common.model.Tags
import com.walletconnect.android.internal.common.model.WCRequest
import com.walletconnect.android.internal.common.model.params.PushParams
import com.walletconnect.android.internal.common.scope
import com.walletconnect.android.pairing.handler.PairingControllerInterface
import com.walletconnect.foundation.common.model.PublicKey
import com.walletconnect.foundation.common.model.Topic
import com.walletconnect.foundation.common.model.Ttl
import com.walletconnect.foundation.util.Logger
import com.walletconnect.push.common.JsonRpcMethod
import com.walletconnect.push.common.PeerError
import com.walletconnect.push.common.model.EngineDO
import com.walletconnect.push.common.model.PushRpc
import com.walletconnect.push.common.model.toEngineDO
import com.walletconnect.push.common.storage.data.SubscriptionStorageRepository
import com.walletconnect.util.generateId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal class WalletEngine(
    private val jsonRpcInteractor: JsonRpcInteractorInterface,
    private val crypto: KeyManagementRepository,
    private val pairingHandler: PairingControllerInterface,
    private val subscriptionStorageRepository: SubscriptionStorageRepository,
    private val logger: Logger,
) {
    private var jsonRpcRequestsJob: Job? = null
    private var jsonRpcResponsesJob: Job? = null
    private var internalErrorsJob: Job? = null
    private val _engineEvent: MutableSharedFlow<EngineEvent> = MutableSharedFlow()
    val engineEvent: SharedFlow<EngineEvent> = _engineEvent.asSharedFlow()
    private val pushRequests: MutableMap<String, WCRequest> = mutableMapOf()

    init {
        pairingHandler.register(
            JsonRpcMethod.WC_PUSH_REQUEST,
            JsonRpcMethod.WC_PUSH_MESSAGE
        )
    }

    fun setup() {
        jsonRpcInteractor.isConnectionAvailable
            .onEach { isAvailable -> _engineEvent.emit(ConnectionState(isAvailable)) }
            .filter { isAvailable: Boolean -> isAvailable }
            .onEach {
                supervisorScope {
                    launch(Dispatchers.IO) {
                        resubscribeToSubscriptions()
                    }
                }

                if (jsonRpcRequestsJob == null) {
                    jsonRpcRequestsJob = collectJsonRpcRequests()
                }

                if (jsonRpcResponsesJob == null) {
                    jsonRpcResponsesJob = collectJsonRpcResponses()
                }

                if (internalErrorsJob == null) {
                    internalErrorsJob = collectInternalErrors()
                }
            }
            .launchIn(scope)
    }

    fun approve(requestId: Long, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        try {
            val peerPublicKey = subscriptionStorageRepository.getPeerPublicKeyByRequestId(requestId)
            val proposerRequest = pushRequests[peerPublicKey]?.also { request ->
                pushRequests.remove(request.topic.value)
            } ?: return onError(GenericException("Unable to find proposer's request"))
            val proposerRequestParams = proposerRequest.params as PushParams.RequestParams

            val selfPublicKey = crypto.generateKeyPair()
            val pushTopic = crypto.generateTopicFromKeyAgreement(selfPublicKey, PublicKey(peerPublicKey))
            val approvalParams = PushParams.RequestResponseParams(selfPublicKey.keyAsHex)
            val irnParams = IrnParams(Tags.PUSH_REQUEST_RESPONSE, Ttl(DAY_IN_SECONDS))

            subscriptionStorageRepository.updateSubscriptionToResponded(requestId, pushTopic.value, proposerRequestParams.metaData)

            jsonRpcInteractor.subscribe(pushTopic) { error ->
                return@subscribe onError(error)
            }
            jsonRpcInteractor.respondWithParams(proposerRequest, approvalParams, irnParams) { error ->
                return@respondWithParams onError(error)
            }

            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun reject(requestId: Long, reason: String, onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        try {
            val peerPublicKey = subscriptionStorageRepository.getPeerPublicKeyByRequestId(requestId)
            val proposerRequest = pushRequests[peerPublicKey]?.also { request ->
                pushRequests.remove(request.topic.value)
            } ?: return onError(GenericException("Unable to find proposer's request"))
            val irnParams = IrnParams(Tags.PUSH_REQUEST_RESPONSE, Ttl(DAY_IN_SECONDS))

            jsonRpcInteractor.respondWithError(proposerRequest, PeerError.EIP1193.UserRejectedRequest(reason), irnParams) { error ->
                return@respondWithError onError(error)
            }

            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun getListOfActiveSubscriptions(): Map<String, EngineDO.PushSubscription.Responded> {
        return subscriptionStorageRepository.getAllSubscriptions()
            .filterIsInstance<EngineDO.PushSubscription.Responded>()
            .associateBy { subscription -> subscription.topic }
    }

    fun delete(topic: String, onFailure: (Throwable) -> Unit) {
        val deleteParams = PushParams.DeleteParams(6000, "User Disconnected")
        val request = PushRpc.PushDelete(id = generateId(), params = deleteParams)
        val irnParams = IrnParams(Tags.PUSH_DELETE, Ttl(DAY_IN_SECONDS))

        subscriptionStorageRepository.delete(topic)

        jsonRpcInteractor.unsubscribe(Topic(topic))
        jsonRpcInteractor.publishJsonRpcRequest(Topic(topic), irnParams, request,
            onSuccess = {
                logger.log("Delete sent successfully")
            },
            onFailure = {
                onFailure(it)
            }
        )
    }

    private fun collectJsonRpcRequests(): Job =
        jsonRpcInteractor.clientSyncJsonRpc
            .filter { request -> request.params is PushParams }
            .onEach { request ->
                when (val requestParams = request.params) {
                    is PushParams.RequestParams -> onPushRequest(request, requestParams)
                    is PushParams.MessageParams -> onPushMessage(request, requestParams)
                    is PushParams.DeleteParams -> onPushDelete(request)
                }
            }.launchIn(scope)

    private fun collectJsonRpcResponses(): Job =
        jsonRpcInteractor.peerResponse.onEach { response ->
            when (val params = response.params) {
                is PushParams.DeleteParams -> onPushDeleteResponse()
            }
        }.launchIn(scope)

    private fun collectInternalErrors(): Job =
        merge(jsonRpcInteractor.internalErrors, pairingHandler.findWrongMethodsFlow)
            .onEach { exception -> _engineEvent.emit(SDKError(exception)) }
            .launchIn(scope)

    private fun onPushRequest(request: WCRequest, params: PushParams.RequestParams) {
        val irnParams = IrnParams(Tags.PUSH_REQUEST_RESPONSE, Ttl(DAY_IN_SECONDS))

        try {
            pushRequests[params.publicKey] = request
            subscriptionStorageRepository.insertSubscriptionRequest(request.id, params.publicKey)

            scope.launch { _engineEvent.emit(params.toEngineDO(request.id)) }
        } catch (e: Exception) {
            jsonRpcInteractor.respondWithError(
                request,
                Uncategorized.GenericError("Cannot handle the push request: ${e.message}, topic: ${request.topic}"),
                irnParams
            )
        }
    }

    private fun onPushMessage(request: WCRequest, params: PushParams.MessageParams) {
        val irnParams = IrnParams(Tags.PUSH_MESSAGE_RESPONSE, Ttl(DAY_IN_SECONDS))

        try {
            jsonRpcInteractor.respondWithSuccess(request, irnParams)
            scope.launch { _engineEvent.emit(params.toEngineDO()) }
        } catch (e: Exception) {
            jsonRpcInteractor.respondWithError(
                request,
                Uncategorized.GenericError("Cannot handle the push message: ${e.message}, topic: ${request.topic}"),
                irnParams
            )
        }
    }

    private fun onPushDelete(request: WCRequest) {
        val irnParams = IrnParams(Tags.PUSH_DELETE_RESPONSE, Ttl(DAY_IN_SECONDS))

        try {
            jsonRpcInteractor.respondWithSuccess(request, irnParams)
            jsonRpcInteractor.unsubscribe(request.topic)
            subscriptionStorageRepository.delete(request.topic.value)

            scope.launch { _engineEvent.emit(EngineDO.PushDelete(request.topic.value)) }
        } catch (e: Exception) {
            scope.launch { _engineEvent.emit(SDKError(InternalError(e))) }
        }
    }

    private fun onPushDeleteResponse() {
        // TODO: Review if we need this
    }

    private fun resubscribeToSubscriptions() {
        subscriptionStorageRepository.getAllSubscriptions()
            .filterIsInstance<EngineDO.PushSubscription.Responded>()
            .forEach { subscription ->
                jsonRpcInteractor.subscribe(Topic(subscription.topic))
            }
    }
}