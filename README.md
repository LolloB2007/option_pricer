# OptionPricer

A Java desktop application for pricing **European call and put options** using the **Black-Scholes model**.

## Release

**Current release: v1.0**

This is the first full release of the application, featuring a graphical user interface and support for pricing European vanilla options through a Black-Scholes engine.

## Overview

OptionPricer is a Java-based desktop finance application built as a passion project.

The application allows the user to input the key parameters of a European option contract and returns the model price through a simple graphical interface.

At the current stage, the application supports:

- **European Call options**
- **European Put options**
- **Black-Scholes pricing**
- **Swing-based graphical user interface**
- **Modernized dark theme styling**
- **Basic user input validation**

## Features

- Price **European Call** options
- Price **European Put** options
- GUI built with **Swing**
- Styled using **FlatLaf**
- Layout managed using **MigLayout**
- Black-Scholes pricing engine
- Standard normal distribution helper functions
- Basic validation for invalid user input
- Clean result display directly in the application window

## Supported Inputs

The current version supports the following user inputs:

- **Option Type** (`Call` or `Put`)
- **Spot Price** (`S`)
- **Strike Price** (`K`)
- **Risk-Free Rate** (`r`)
- **Volatility** (`σ`)
- **Time to Expiry** (`T`)

## Input Conventions

Please enter values using the following conventions:

- **Risk-Free Rate** must be entered in **decimal form**
  - Example: `6% = 0.06`

- **Volatility** must be entered in **decimal form**
  - Example: `15% = 0.15`

- **Time to Expiry** must be entered in **years**
  - Example: `6 months = 0.5`

## Run

### Option 1: Prebuilt JAR

**click here for jar file**

> Requires **Java 17**.

### Option 2: Run from Source

#### Requirements

- **Java 17**
- **Maven**

#### Build

```bash
mvn clean package
```

## Tech Stack

- Java 17
- Maven
- Swing
- FlatLaf
- MigLayout

## Validation Rules

The application expects valid numeric inputs and currently works under these assumptions:

- spot price must be positive
- strike price must be positive
- risk-free rate must be entered in decimal form
- volatility must be entered in decimal form
- time to expiry must be positive and entered in years

Examples:

- `6% = 0.06`
- `15% = 0.15`
- `6 months = 0.5`

## Current Limitations

At the moment, the application is limited to:

- European options only
- Black-Scholes pricing only
- no Greeks output yet
- no implied volatility solver
- no payoff chart yet
- no additional pricing models such as Binomial or Monte Carlo

## Roadmap

Possible future improvements:

- add Greeks output
- add payoff diagrams / charting
- improve validation feedback and error handling
- improve result formatting
- add additional pricing models
- support more advanced option types
- improve packaging and distribution

## Motivation

This project was built as a passion project to combine:

- Java desktop development
- GUI design
- numerical finance
- mathematical modelling

## Author

**Lorenzo Barbagelata**

## License

This project is distributed under a **Custom Proprietary License**.

You may:

- use the software for personal, educational, or internal evaluation purposes

You may not:

- modify, adapt, alter, translate, or create derivative works of the software
- redistribute, rehost, mirror, sublicense, sell, or independently distribute the source code or any compiled binary
- publish modified or unmodified copies of the software
- remove copyright or authorship notices

All rights not expressly granted are reserved by the author.

If you would like permission to modify, redistribute, or use this software beyond the scope above, please contact the author directly.
