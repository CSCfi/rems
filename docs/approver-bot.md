# Approver Bot

REMS has a bot which approves applications automatically, unless the user+resource is blacklisted. To use the approver bot, insert a user with the ID "approver-bot" to the database and add it as a handler to a workflow where the applications should be auto-approved.

If the applicant or any of the members has been blacklisted for an applied resource, the bot will do nothing and a human will need to handle the application.
