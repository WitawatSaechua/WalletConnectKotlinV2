@file:JvmSynthetic

package com.walletconnect.sign.common.model.vo.clientsync.session

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.walletconnect.android.internal.common.model.JsonRpcClientSync
import com.walletconnect.sign.common.model.vo.clientsync.session.params.SignParams
import com.walletconnect.sign.json_rpc.model.JsonRpcMethod

internal sealed class SignRpc : JsonRpcClientSync<SignParams> {
    abstract override val id: Long
    abstract override val method: String
    abstract override val jsonrpc: String
    abstract override val params: SignParams

    @JsonClass(generateAdapter = true)
    internal data class SessionPropose(
        @Json(name = "id")
        override val id: Long,
        @Json(name = "jsonrpc")
        override val jsonrpc: String = "2.0",
        @Json(name = "method")
        override val method: String = JsonRpcMethod.WC_SESSION_PROPOSE,
        @Json(name = "params")
        override val params: SignParams.SessionProposeParams,
    ) : SignRpc()

    @JsonClass(generateAdapter = true)
    internal data class SessionSettle(
        @Json(name = "id")
        override val id: Long,
        @Json(name = "jsonrpc")
        override val jsonrpc: String = "2.0",
        @Json(name = "method")
        override val method: String = JsonRpcMethod.WC_SESSION_SETTLE,
        @Json(name = "params")
        override val params: SignParams.SessionSettleParams
    ) : SignRpc()

    @JsonClass(generateAdapter = true)
    internal data class SessionRequest(
        @Json(name = "id")
        override val id: Long,
        @Json(name = "jsonrpc")
        override val jsonrpc: String = "2.0",
        @Json(name = "method")
        override val method: String = JsonRpcMethod.WC_SESSION_REQUEST,
        @Json(name = "params")
        override val params: SignParams.SessionRequestParams
    ) : SignRpc()

    @JsonClass(generateAdapter = true)
    internal data class SessionDelete(
        @Json(name = "id")
        override val id: Long,
        @Json(name = "jsonrpc")
        override val jsonrpc: String = "2.0",
        @Json(name = "method")
        override val method: String = JsonRpcMethod.WC_SESSION_DELETE,
        @Json(name = "params")
        override val params: SignParams.DeleteParams
    ) : SignRpc()

    @JsonClass(generateAdapter = true)
    internal data class SessionPing(
        @Json(name = "id")
        override val id: Long,
        @Json(name = "jsonrpc")
        override val jsonrpc: String = "2.0",
        @Json(name = "method")
        override val method: String = JsonRpcMethod.WC_SESSION_PING,
        @Json(name = "params")
        override val params: SignParams.PingParams,
    ) : SignRpc()

    @JsonClass(generateAdapter = true)
    internal data class SessionEvent(
        @Json(name = "id")
        override val id: Long,
        @Json(name = "jsonrpc")
        override val jsonrpc: String = "2.0",
        @Json(name = "method")
        override val method: String = JsonRpcMethod.WC_SESSION_EVENT,
        @Json(name = "params")
        override val params: SignParams.EventParams,
    ) : SignRpc()

    @JsonClass(generateAdapter = true)
    internal data class SessionUpdate(
        @Json(name = "id")
        override val id: Long,
        @Json(name = "jsonrpc")
        override val jsonrpc: String = "2.0",
        @Json(name = "method")
        override val method: String = JsonRpcMethod.WC_SESSION_UPDATE,
        @Json(name = "params")
        override val params: SignParams.UpdateNamespacesParams,
    ) : SignRpc()

    @JsonClass(generateAdapter = true)
    internal data class SessionExtend(
        @Json(name = "id")
        override val id: Long,
        @Json(name = "jsonrpc")
        override val jsonrpc: String = "2.0",
        @Json(name = "method")
        override val method: String = JsonRpcMethod.WC_SESSION_EXTEND,
        @Json(name = "params")
        override val params: SignParams.ExtendParams,
    ) : SignRpc()
}