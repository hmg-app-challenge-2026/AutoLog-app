package kr.autolog.vehicle.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.autolog.vehicle.core.ContextApi

interface ContextRepository {
    /** 내비 목적지 이름. 없으면 null. */
    val destinationLabel: Flow<String?>
}

class DefaultContextRepository(api: ContextApi) : ContextRepository {
    override val destinationLabel: Flow<String?> = api.destination.map { it?.label }
}
