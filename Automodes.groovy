/**
*  Automodes
*  Author: Christian Madden
*/

/* ************************************************************************** */
definition(
  name: "Automodes",
  namespace: "christianmadden",
  author: "Christian Madden",
  description: "Automate changing of modes based on time of day and presence",
  category: "Convenience",
  iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
  iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

/* ************************************************************************** */
preferences
{
  section("Automodes: Automate changing of modes with Hello Home phrases based on time of day and presence.")
  {
    paragraph "Please create modes and phrases following this naming convention before continuing."
    paragraph "Modes: '<Name>' and '<Name>, Away'"
    paragraph "Phrases: 'Set Home To <Mode>'"
  }
  section("Change modes based on these presence sensors")
  {
    input "presence", "capability.presenceSensor", multiple: true, title: "Which?"
  }
  section("Mode names to use at sunrise and sunset")
  {
    input "sunriseModeName", "mode", title: "Sunrise Mode"
    input "sunsetModeName", "mode", title: "Sunset Mode"
  }
  section("Custom daypart 1 of 2", hidden: true)
  {
    input "customOneTime", "time", title: "Starts at this time every day", required: false
    input "customOneModeName", "mode", title: "Mode", required: false
  }
  section("Custom daypart 2 of 2", hidden: true)
  {
    input "customTwoTime", "time", title: "Starts at this time every day", required: false
    input "customTwoModeName", "mode", title: "Mode", required: false
  }
}

/* ************************************************************************** */
def installed()
{
  initialize()
}

/* ************************************************************************** */
def updated()
{
  unsubscribe()
  initialize()
}

/* ************************************************************************** */
def initialize()
{
  log.debug "Initializing... version 0.3"

  state.modepreFix = ""
  state.modeSuffix = ", Away"
  state.phrasePrefix = "Set Home To"
  state.phraseSuffix = ""

  subscribe(people, "presence", onPresence)
  subscribe(location, "sunrise", onSunrise)
  subscribe(location, "sunset", onSunset)

  schedule(customOneTime, onCustomOne)
  schedule(customTwoTime, onCustomTwo)

  initializePresence()
  initializeDaypart()
  update()
}

/* ************************************************************************** */
def initializePresence()
{
  log.debug "Determining initial presence state..."
  state.presence = anyoneIsHome()
}

/* ************************************************************************** */
def initializeDaypart()
{
  log.debug "Determining initial daypart state..."

  def sun = getSunriseAndSunset()
  def now = now()

  def dayparts = [sunrise: sun.sunrise.time,
                  sunset: sun.sunset.time,
                  customOne: customOneTime,
                  customTwo: customTwoTime].sort()

  log.debug dayparts

  /*
  if(timeOfDayIsBetween(dayparts[0], dayparts[1], now))
  {
    log.debug dayparts[0]
  }
  else if(timeOfDayIsBetween(dayparts[1], dayparts[2], now))
  {
    log.debug dayparts[1]
  }
  else if(timeOfDayIsBetween(dayparts[2], dayparts[3], now))
  {
    log.debug dayparts[2]
  }
  else
  {
    log.debug dayparts[3]
  }
  */
}

/* ************************************************************************** */
def onPresence(evt)
{
  state.presence = anyoneIsHome()
  update()

  /*if(evt.value == "not present")
  {
    if(noPresencesFound())
    {
      state.presence = false
    }
    else
    {
      state.presence = true
    }
  }
  else
  {
    state.presence = true
  }
  */
}

/* ************************************************************************** */
def onSunrise()
{
  state.daypart = "sunrise"
  update()
}


/* ************************************************************************** */
def onSunset()
{
  state.daypart = "sunset"
  update()
}


/* ************************************************************************** */
def onCustomOne()
{
  state.daypart = "custom one"
  update()
}

/* ************************************************************************** */
def onCustomTwo()
{
  state.daypart = "custom two"
  update()
}

/* ************************************************************************** */
private everyoneIsAway()
{
  if(people.findAll { it?.currentPresence == "present" })
  {
    return false
  }
}

/* ************************************************************************** */
private anyoneIsHome(){ return !everyoneIsAway() }


/* ************************************************************************** */
private update()
{
  def mode
  def phrase

  if(state.daypart == "sunrise")
  {
    mode = sunriseModeName
  }
  else if(state.daypart == "sunset")
  {
    mode = sunsetModeName
  }
  else if(state.daypart == "custom one")
  {
    mode = customOneModeName
  }
  else if(state.daypart == "custom two")
  {
    mode = customTwoModeName
  }

  if(!state.presence)
  {
    mode = mode + ", Away"
  }

  phrase = "Set Home To " + mode

  setMode(mode)
  executePhrase(phrase)
}

/* ************************************************************************** */
private setMode(mode)
{
  log.debug "Setting mode to: " + mode + " (from : " + location.mode + ")"
  if(location.mode != mode)
  {
    if(location.modes?.find{it.name == mode})
    {
      setLocationMode(mode)
      sendNotificationEvent("${label} has changed the mode to '${mode}'")
    }
    else
    {
      sendNotificationEvent("${label} tried to change to undefined mode '${mode}'")
    }
  }
  else
  {
    log.debug "Mode is already set to '${mode}'"
  }
}

/* ************************************************************************** */
private executePhrase(phrase)
{
  log.debug "Executing phrase: " + phrase

  if(location.helloHome?.getPhrases().find{ it?.label == phrase })
  {
    location.helloHome.execute(phrase)
    sendNotificationEvent("${phrase}")
  }
  else
  {
    sendNotificationEvent("${phrase}' is undefined")
  }
}
