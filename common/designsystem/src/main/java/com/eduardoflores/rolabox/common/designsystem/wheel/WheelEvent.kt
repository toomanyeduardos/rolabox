package com.eduardoflores.rolabox.common.designsystem.wheel

/**
 * What the user did to the wheel: the fixed vocabulary of ADR-018. An event says what happened, not
 * what it does; `:app` and the screens decide that.
 */
sealed interface WheelEvent {
    /**
     * The ring was turned by [steps]: positive is clockwise, negative is counter-clockwise, and it is
     * never zero. Acceleration is already applied, so a screen moves its highlight or volume by
     * exactly this many.
     */
    data class Turn(val steps: Int) : WheelEvent {
        init {
            require(steps != 0) { "A turn is at least one step" }
        }
    }

    /** The center button was pressed. */
    data object Center : WheelEvent

    /** The MENU button was pressed. */
    data object Menu : WheelEvent

    /** The MENU button was pressed and held. Reported once, when the hold is recognized. */
    data object HoldMenu : WheelEvent

    /** The ⏮ button was pressed. */
    data object Previous : WheelEvent

    /** The ⏭ button was pressed. */
    data object Next : WheelEvent

    /** The ⏮ button is held: [HoldState.Held] when the hold starts, [HoldState.Released] when it ends. */
    data class HoldPrevious(val state: HoldState) : WheelEvent

    /** The ⏭ button is held: [HoldState.Held] when the hold starts, [HoldState.Released] when it ends. */
    data class HoldNext(val state: HoldState) : WheelEvent

    /** The ⏯ button was pressed. */
    data object PlayPause : WheelEvent
}

/** The two moments of a button that is held until it is released. */
enum class HoldState { Held, Released }
