# Folia Phantom

Folia Phantom is a powerful tool designed to patch Bukkit plugins, ensuring their compatibility with the high-performance Folia server. It operates by dynamically transforming the plugin's bytecode to replace thread-unsafe API calls with their Folia-supported equivalents.

This project is structured as a multi-module Maven application, providing both a standalone command-line interface (CLI) for offline patching and a Bukkit plugin for on-the-fly transformations.

## Project Structure

- `folia-phantom-core`: A library module containing the essential patching logic, including the ASM transformers and the `PluginPatcher` utility.
- `folia-phantom-cli`: A command-line application that depends on the `core` module and produces a runnable JAR for patching plugins.
- `folia-phantom-plugin`: A Bukkit plugin that also depends on the `core` module and is intended for server environments.

## Building the Project

To build the project, you will need [Apache Maven](httpshttps://maven.apache.org/install.html) and a Java Development Kit (JDK) version 17 or higher.

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/folia-phantom.git
   cd folia-phantom
   ```

2. **Build the project using Maven:**
   ```bash
   mvn clean package
   ```

This command will compile the source code, run any tests, and package the artifacts. The resulting JAR files will be located in the `target` directory of their respective modules.

## Usage

### Command-Line Interface (CLI)

The CLI allows you to patch a single JAR file or an entire directory of JARs.

1. **Locate the CLI JAR:**
   The runnable CLI JAR, named `Folia-Phantom-CLI-1.0.0.jar`, can be found in the `folia-phantom/folia-phantom-cli/target` directory after a successful build.

2. **Run the CLI:**
   - **Interactive Mode:**
     ```bash
     java -jar Folia-Phantom-CLI-1.0.0.jar
     ```
     The application will prompt you to enter the path to the JAR file or directory you wish to patch.

   - **Direct Mode:**
     ```bash
     java -jar Folia-Phantom-CLI-1.0.0.jar /path/to/your/plugin.jar
     ```
     Replace `/path/to/your/plugin.jar` with the actual path to the plugin file or directory.

Patched plugins will be saved in the `patched-plugins` directory, which is created in the same location where you run the command.

### Bukkit Plugin

The Bukkit plugin is designed to be installed on a Folia server to provide on-the-fly patching.

1. **Locate the Plugin JAR:**
   The plugin JAR, named `Folia-Phantom-Plugin-1.0.0.jar`, can be found in the `folia-phantom/folia-phantom-plugin/target` directory.

2. **Install the Plugin:**
   Copy the JAR file into your server's `plugins` directory and restart the server.

## How It Works

Folia Phantom uses the [ASM bytecode manipulation library](https://asm.ow2.io/) to inspect and modify the class files within a plugin's JAR. It identifies and replaces calls to Bukkit API methods that are not thread-safe with their Folia-compatible counterparts. This process is handled by a series of `ClassTransformer` implementations, each targeting a specific set of API methods.

## Contributing

Contributions to Folia Phantom are welcome! Please feel free to open an issue or submit a pull request on our [GitHub repository](https.github.com/your-username/folia-phantom).

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
