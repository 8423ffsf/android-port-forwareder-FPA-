package yuki.androidportforwarder.data.model

import java.io.Serializable

data class ForwardRule(
    var ruleName: String = "",
    var localPort: Int = 0,
    var targetAddress: String = "",
    var targetPort: Int = 0,
    var isUDP: Boolean = false,
    var isActive: Boolean = false
) : Serializable