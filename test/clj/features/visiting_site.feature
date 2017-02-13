Feature: Visiting site
  As a new user, that has not signed in
  I want to get familiar with the application

  Scenario: User Logs In
    Given I am at the "homepage"
    Then I should see "Login"
