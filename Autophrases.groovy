/**
*  Autophrases
*  Author: Christian Madden
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
  page(name: "prefsPage", title: "Autophrases", nextPage: "sunPage", install: false, uninstall: true)
  page(name: "sunPage", title: "Sunrise/Sunset", nextPage: "customOnePage", install: true, uninstall: true)
  page(name: "customOnePage", title: "Custom Daypart 1", nextPage: "customTwoPage", install: true, uninstall: true)
  page(name: "customTwoPage", title: "Custom Daypart 2", install: true, uninstall: true)
}

/* ************************************************************************** */
def prefsPage()
{
  state.phrases = (location.helloHome?.getPhrases()*.label).sort()
  dynamicPage(name: "prefsPage")
  {
    section("Automate Hello Home phrases based on time of day and presence."){}
    section("When any of these people are home or away")
    {
      input "people", "capability.presenceSensor", title: "Which?", multiple: true, required: true
    }
  }
}

/* ************************************************************************** */
def sunPage()
{
  dynamicPage(name: "sunPage")
  {
    section("Run these phrases at sunrise and sunset when home or away")
    {
      input "sunrisePhrase", "enum", options: state.phrases, title: "Use this phrase at sunrise when home", required: true
      input "sunrisePhraseAway", "enum", options: state.phrases, title: "Use this phrase at sunrise when away", required: true
      input "sunsetPhrase", "enum", options: state.phrases, title: "Use this phrase at sunset when home", required: true
      input "sunrsetPhraseAway", "enum", options: state.phrases, title: "Use this phrase at sunset when away", required: true
    }
  }
}

/* ************************************************************************** */
def customOnePage(page)
{
  dynamicPage(name: "customTwoPage")
  {
    section("Run these phrases at a custom time when home or away (optional)")
    {
      input "customTwoTime", "time", title: "Starts at this time every day", required: false
      input "customTwoPhrase", "enum", options: state.phrases, title: "Use this phrase when home", required: false
      input "customTwoPhraseAway", "enum", options: state.phrases, title: "Use this phrase when home", required: false

    }
  }
}

/* ************************************************************************** */
def customTwoPage(page)
{
  def phrases = (location.helloHome?.getPhrases()*.label).sort()
  dynamicPage(name: page + "Page")
  {
    section("Run these phrases at a custom time when home or away (optional)")
    {
      input "customTwoTime", "time", title: "Starts at this time every day", required: false
      input "customTwoPhrase", "enum", options: state.phrases, title: "Use this phrase when home", required: false
      input "customTwo"PhraseAway", "enum", options: state.phrases, title: "Use this phrase when home", required: false

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
  initialize()
}

/* ************************************************************************** */
def initialize()
{
  log.debug "Initializing..."
  log.debug location.helloHome?.getPhrases()*.label

  subscribe(people, "presence", onPresence)
  subscribe(location, "sunrise", onSunrise)
  subscribe(location, "sunset", onSunset)

  schedule(customOneTime, onCustomOne)
  schedule(customTwoTime, onCustomTwo)

  //initializePresence()
  //initializeDaypart()
  //update()
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

  /*
  def sun = getSunriseAndSunset()
  def now = timeToday(new Date(), location.timeZone)

  log.debug now

  def dayparts = [sunrise: timeToday(sun.sunrise, location.timeZone),
                  sunset: timeToday(sun.sunset, location.timeZone),
                  customOne: timeToday(customOneTime, location.timeZone),
                  customTwo: timeToday(customTwoTime, location.timeZone)].sort { it.value }

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
  */
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
