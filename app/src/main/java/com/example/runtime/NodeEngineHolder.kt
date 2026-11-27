package com.example.runtime

object NodeEngineHolder {
    val engine: NodeEngine by lazy { NodeEngine() }
}
