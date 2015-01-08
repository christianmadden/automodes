/**
*  Autophrases
*  Author: Christian Madden
*  TODO: Use ${}
*/

/* ************************************************************************** */
definition(
name: "Autophrases",
namespace: "christianmadden",
author: "Christian Madden",
description: "Automate Hello Home phrases based on time of day and presence",
category: "Convenience",
iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

/* ************************************************************************** */
preferences
{
  page(name: "prefsPage", title: "Automate Hello Home phrases based on time of day and presence.", uninstall: true, install: true)
}

/* ************************************************************************** */
def prefsPage()
{
  state.phrases = (location.helloHome?.getPhrases()*.label).sort()

  dynamicPage(name: "prefsPage")
  {
    section("When any of these people are home or away")
    {
      input "people", "capability.presenceSensor", title: "Which?", multiple: true, required: true
    }
    section("Run these phrases at sunrise and sunset when home or away")
    {
      input "sunrisePhrase", "enum", options: state.phrases, title: "Use this phrase at sunrise when home", required: true
      input "sunrisePhraseAway", "enum", options: state.phrases, title: "Use this phrase at sunrise when away", required: true
      input "sunsetPhrase", "enum", options: state.phrases, title: "Use this phrase at sunset when home", required: true
      input "sunsetPhraseAway", "enum", options: state.phrases, title: "Use this phrase at sunset when away", required: true
    }
    section("Run these phrases at a custom time when home or away (optional)")
    {
      input "customOneTime", "time", title: "Starts at this time every day", required: false
      input "customOnePhrase", "enum", options: state.phrases, title: "Use this phrase when home", required: false
      input "customOnePhraseAway", "enum", options: state.phrases, title: "Use this phrase when away", required: false
    }
    section("Run these phrases at a custom time when home or away (optional)")
    {
      input "customTwoTime", "time", title: "Starts at this time every day", required: false
      input "customTwoPhrase", "enum", options: state.phrases, title: "Use this phrase when home", required: false
      input "customTwoPhraseAway", "enum", options: state.phrases, title: "Use this phrase when away", required: false
    }
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
  unschedule()
  initialize()
}

/* ************************************************************************** */
def initialize()
{
  log.debug "Initializing..."
  log.debug "Settings:"
  log.debug settings
  log.debug "-----"

  state.HOME = "home"
  state.AWAY = "away"

  subscribe(people, "presence", onPresence)

  subscribe(location, "sunrise", onSunrise)
  subscribe(location, "sunset", onSunset)

  if(settings.customOneTime && settings.customOnePhrase && settings.customOnePhraseAway)
  {
    log.debug "Scheduling Custom One..."
    schedule(customOneTime, onCustomOne)
  }

  if(settings.customTwoTime && settings.customTwoPhrase && settings.customTwoPhraseAway)
  {
    log.debug "Scheduling Custom Two..."
    schedule(customTwoTime, onCustomTwo)
  }

  initializePresence()
  //initializeDaypart()
  updatePhrase()
}

/* ************************************************************************** */
def initializePresence()
{
  log.debug "Determining initial presence state..."
  if(anyoneIsHome())
  {
    log.debug "Initial presence: ${state.HOME}"
    state.presence = state.HOME
  }
  else
  {
    log.debug "Initial presence: ${state.AWAY}"
    state.presence = state.AWAY
  }
}

/* ************************************************************************** */
def initializeDaypart()
{
  log.debug "Determining initial daypart state..."
  log.debug "Time zone: ${location.timeZone}"

  def now = new Date()
  log.debug now

  def sun = getSunriseAndSunset()

  def dayparts = []
  dayparts["sunrise"] = timeToday(sun.sunrise, location.timeZone)
  dayparts["sunset"] = timeToday(sun.sunrise, location.timeZone)
  dayparts["customOne"] = timeToday(customOneTime, location.timeZone)
  dayparts["customTwo"] = timeToday(customTwoTime, location.timeZone)
  log.debug dayparts

  def daypartsSorted = dayparts.sort { it.value }
  log.debug daypartsSorted
}

/* ************************************************************************** */
def onPresence(evt)
{
  log.debug "Presence event..."
  log.debug evt.name + " | " + evt.value

  def newPresence

  if(evt.value == "not present")
  {
    if(everyoneIsAway())
    {
      newPresence = state.AWAY
    }
    else
    {
      newPresence = state.HOME
    }
  }
  else if(evt.value == "present")
  {
    newPresence = state.HOME
  }

  log.debug "New presence: " + newPresence
  log.debug "Current presence: " + state.presence

  if(newPresence != state.presence)
  {
    state.presence = newPresence
    updatePhrase()
  }
}

/* ************************************************************************** */
def onSunset(evt)
{
  log.debug "Sunrise..."
  onDaypartChange("sunrise")
}

/* ************************************************************************** */
def onSunrise(evt)
{
  log.debug "Sunset..."
  onDaypartChange("sunset")
}

/* ************************************************************************** */
def onCustomOne(evt)
{
  log.debug "Custom One..."
  onDaypartChange("customOne")
}

/* ************************************************************************** */
def onCustomTwo(evt)
{
  log.debug "Custom Two..."
  onDaypartChange("customTwo")
}

/* ************************************************************************** */
private onDaypartChange(dp)
{
  log.debug "New daypart: " + dp
  log.debug "Current daypart: " + state.daypart

  if(dp != state.daypart)
  {
    state.daypart = dp
    log.debug "Daypart changed to: " + state.daypart
    updatePhrase()
  }
}

/* ************************************************************************** */
private updatePhrase()
{
  log.debug "Updating phrase..."
  def phrase = getPhrase()
  executePhrase(phrase)
}

/* ************************************************************************** */
private getPhrase()
{
  log.debug "The current presence state is: " + state.presence
  log.debug "The current daypart state is: " + state.daypart

  def phrase

  if(state.daypart == "sunrise")
  {
    if(state.presence == state.HOME){ phrase = settings.sunrisePhrase }
    else if(state.presence == state.AWAY){ phrase = settings.sunrisePhraseAway }
  }
  else if(state.daypart == "sunset")
  {
    if(state.presence == state.HOME){ phrase = settings.sunsetPhrase }
    else if(state.presence == state.AWAY){ phrase = settings.sunsetPhraseAway }
  }
  else if(state.daypart == "customOne")
  {
    if(state.presence == state.HOME){ phrase = settings.customOnePhrase }
    else if(state.presence == state.AWAY){ phrase = settings.customOnePhraseAway }
  }
  else if(state.daypart == "customTwo")
  {
    if(state.presence == state.HOME){ phrase = settings.customTwoPhrase }
    else if(state.presence == state.AWAY){ phrase = settings.customTwoPhraseAway }
  }

  return phrase
}

/* ************************************************************************** */
private executePhrase(phrase)
{
  log.debug "Executing phrase: " + phrase
  location.helloHome.execute(phrase)
}

/* ************************************************************************** */
private everyoneIsAway()
{
  return people.every{ it.currentPresence == "not present" }
}

/* ************************************************************************** */
private anyoneIsHome()
{
  return people.any{ it.currentPresence == "present" }
}



/*

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

TIME CODE FROM GITHUB
log.debug "time zone: ${location.timeZone}"

def now = new Date()
def yesterday = now - 1
def tomorrow = now + 1

log.debug "now: ${now}"
log.debug "yesterday: ${yesterday}"
log.debug "tomorrow: ${tomorrow}"

def between = timeOfDayIsBetween(yesterday, tomorrow, now, location.timeZone)
log.debug "between: ${between}"

*/
