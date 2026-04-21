# URL: https://www.saucedemo.com/
# Username field: id="user-name" | xpath="//input[@id='user-name']"
# Password field: id="password" | xpath="//input[@id='password']"
# Login button: id="login-button" | xpath="//input[@id='login-button']"
# Login form: tag="form" | xpath="//form"
# Accepted usernames header: text="Accepted usernames are:" | xpath="//h4[normalize-space()='Accepted usernames are:']"
# Password for all users header: text="Password for all users:" | xpath="//h4[normalize-space()='Password for all users:']"

Feature: User Authentication on Swag Labs

  Background:
    Given the user is on the Swag Labs login page

  Scenario: Successful login with valid credentials
    Given the Username field is visible and enabled
    And the Password field is visible and enabled
    When the user enters "standard_user" into the Username field
    And the user enters "secret_sauce" into the Password field
    And the user clicks the Login button
    Then the user should be redirected to the products page

  Scenario: Unsuccessful login with invalid username
    Given the Username field is visible and enabled
    And the Password field is visible and enabled
    When the user enters "invalid_user" into the Username field
    And the user enters "secret_sauce" into the Password field
    And the user clicks the Login button
    Then an error message should be displayed indicating invalid credentials

  Scenario: Unsuccessful login with invalid password
    Given the Username field is visible and enabled
    And the Password field is visible and enabled
    When the user enters "standard_user" into the Username field
    And the user enters "wrong_password" into the Password field
    And the user clicks the Login button
    Then an error message should be displayed indicating invalid credentials

  Scenario: Unsuccessful login with empty username and password
    Given the Username field is visible and enabled
    And the Password field is visible and enabled
    When the user leaves the Username field empty
    And the user leaves the Password field empty
    And the user clicks the Login button
    Then an error message should be displayed indicating missing credentials

  Scenario: Verify visibility of login form elements
    Then the Username field should be visible
    And the Password field should be visible
    And the Login button should be visible
    And the Accepted usernames header should be visible
    And the Password for all users header should be visible