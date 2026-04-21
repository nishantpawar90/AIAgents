# URL: https://www.saucedemo.com/
# Username field: id="user-name" | xpath="//input[@id='user-name']"
# Password field: id="password" | xpath="//input[@id='password']"
# Login button: id="login-button" | xpath="//input[@id='login-button']"
# Add to cart button (Sauce Labs Bike Light): id="add-to-cart-sauce-labs-bike-light" | xpath="//button[@id='add-to-cart-sauce-labs-bike-light']"
# Cart icon: class="shopping_cart_link" | xpath="//a[@class='shopping_cart_link']"

Feature: User can log in and add items to the cart on Swag Labs

  Background:
    Given the user is on the Swag Labs login page

  Scenario: Successful login with valid credentials
    When the user enters "standard_user" into the Username field
    And the user enters "secret_sauce" into the Password field
    And the user clicks the Login button
    Then the user should be redirected to the products page

  Scenario: Add Sauce Labs Bike Light to the cart
    Given the user is logged in with valid credentials
    When the user clicks the Add to cart button for Sauce Labs Bike Light
    Then the item should be added to the cart

  Scenario: View cart after adding an item
    Given the Sauce Labs Bike Light is added to the cart
    When the user clicks the Cart icon
    Then the cart should display the Sauce Labs Bike Light

  Scenario: End-to-end - Login and add Sauce Labs Bike Light to cart
    When the user enters "standard_user" into the Username field
    And the user enters "secret_sauce" into the Password field
    And the user clicks the Login button
    Then the user should be redirected to the products page
    When the user clicks the Add to cart button for Sauce Labs Bike Light
    Then the item should be added to the cart
    When the user clicks the Cart icon
    Then the cart should display the Sauce Labs Bike Light