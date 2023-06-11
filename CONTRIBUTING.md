# How to contribute

Contributions are welcome!

* Please file bug reports and feature requests to https://github.com/metosin/oksa/issues
* For small changes, such as bug fixes or documentation changes, feel free to send a pull request
* If you want to make a big change or implement a big new feature, please open an issue to discuss it first

## Environment setup

1. Clone this git repository
2. Have [Clojure installed](https://clojure.org/guides/getting_started)

## Making changes

* Fork the repository on Github
* Create a topic branch from where you want to base your work (usually the main branch)
* Check the formatting rules from existing code (no trailing whitespace, mostly default indentation)
* Ensure any new code is well-tested, and if possible, any issue fixed is covered by one or more new tests
* Verify that all tests pass using `./bin/kaocha`, `./bin/node`, and `./bin/karma` (for first run of karma, run `npm ci` or `npm install`)
* Push your code to your fork of the repository
* Make a Pull Request

## Commit messages

1. Separate subject from body with a blank line
2. Limit the subject line to 50 characters
3. Capitalize the subject line
4. Do not end the subject line with a period
5. Use the imperative mood in the subject line
    - "Add x", "Fix y", "Support z", "Remove x"
6. Wrap the body at 72 characters
7. Use the body to explain what and why vs. how
