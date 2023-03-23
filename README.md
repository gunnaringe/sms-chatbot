# SMS Chatbot

This is a simple SMS chatbot that uses the WG2 API to send and receive SMS messages.

It is hooked up to ChatGPT for generating responses.


## Configuration

An example configuration file is provided in `example/config.yaml`.

All fields may be overridden by environment variables, using the following naming scheme:

```shell
FIELD__SUBFIELD__NAME_OF_FIELD
```

## Usage
```shell
set -x WG2__CLIENT_ID <client_id>
set -X WG2__CLIENT_SECRET <client_secret>
set -x OPENAI__API_KEY <api_key>
set -x PHONES <phone_number>,<phone_number>,...

mvn clean package
java -jar target/sms-chat-1.0-SNAPSHOT.jar example/config.yaml
```

# Add executor for event handling
