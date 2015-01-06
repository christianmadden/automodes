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
  section("Add a custom daypart (optional)", hidden: true)
  {
    input "customOneTime", "time", title: "Starts at this time every day", required: false
    input "customOneModeName", "mode", title: "Mode", required: false
  }
  section("Add another custom daypart (optional)", hidden: true)
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
  log.debug "Initializing..."

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
  def now = new Date()
  def tz = TimeZone.getTimeZone(locale.location.tz_long)

  log.debug now

  def dayparts = [sunrise: sun.sunrise,
                  sunset: sun.sunset,
                  customOne: timeToday(customOneTime, tz),
                  customTwo: timeToday(customTwoTime, tz)].sort { it.value }

  log.debug dayparts

  log.debug dayparts[0]
  log.debug dayparts[1]
  log.debug dayparts[2]
  log.debug dayparts[3]
  log.debug now.toString()

  if(timeOfDayIsBetween(dayparts[0], dayparts[1], now))
  {
    state.daypart = dayparts[0].key
  }
  else if(timeOfDayIsBetween(dayparts[1], dayparts[2], now))
  {
    state.daypart = dayparts[1].key
  }
  else if(timeOfDayIsBetween(dayparts[2], dayparts[3], now))
  {
    state.daypart = dayparts[2].key
  }
  else
  {
    state.daypart = dayparts[3].key
  }

  log.debug state.daypart
}

/* ************************************************************************** */
def onPresence(evt)
{
  if(evt.value == "not present")
  {
    if(everyoneIsAway())
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

  update()
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
  log.debug "The current presence state is: " + state.presence
  log.debug "The current daypart state is: " + state.daypart

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
