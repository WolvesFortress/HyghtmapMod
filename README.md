# HyghtmapMod

A Hytale mod for importing and managing heightmap files for terrain generation.

## Features

- **File Format Support**:
  - 8-bit grayscale or RGB (PNG, BMP, JPEG, TGA – any format Java ImageIO reads)
  - 16-bit grayscale PNG (sampled via red-channel high byte)
  - Raw 32-bit float little-endian binary (.f32) – single-channel, width×height
  - Raw 16-bit float little-endian binary (.f16) – single-channel, width×height

- **Import Modes**:
  - **HEIGHTMAP** – stacked column of blocks, one per unit of height
  - **SURFACE** – single block per (X,Z) column at the derived Y level (hollow)
  - **COLORMAP** – flat image-to-block colour match, like ImageImportPage (ignores height)
  - **NORMALMAP** – derives a surface shape from a normal-map image

- **Core Features**:
  - Interactive file browser with search functionality
  - Preview panel with dimension estimates and block count
  - Height scale controls (1-320 blocks)
  - Maximum size limits (1-1024 blocks for performance)
  - Channel selection (luminance, red, green, blue, alpha)
  - Origin positioning options
  - Invert height option
  - Smooth/blur option for heightmaps
  - Block pattern customization
  - Colormap support for COLORMAP and NORMALMAP modes

## Installation

1. Place the mod files in your Hytale mods directory
2. Restart Hytale server/client
3. The mod will be automatically loaded

## Usage

### Commands

#### `/heightmap`
Opens the heightmap import dialog where you can:
- Browse and select heightmap files
- Preview file information and dimensions
- Configure import settings
- Import heightmaps for terrain generation
- **Automatically switches to paste tool after import** for easy placement

### File Locations

Heightmap files should be placed in your Hytale server's asset pack directory:
```
<YourHytaleServer>/Server/Imports/Heightmaps/
```

To find your Hytale server directory:
1. Locate your Hytale server installation folder
2. Navigate to the `Server/Imports/` subdirectory  
3. Create a `Heightmaps` folder if it doesn't exist
4. Place your heightmap files there

Supported file formats:
- `.png` - PNG images (8-bit grayscale, 16-bit grayscale, RGB)
- `.bmp` - BMP images (8-bit)
- `.jpg` / `.jpeg` - JPEG images (any format Java ImageIO reads)
- `.tga` - TGA images (any format Java ImageIO reads)
- `.f32` - Raw 32-bit float little-endian binary (single-channel, width×height)
- `.f16` - Raw 16-bit float little-endian binary (single-channel, width×height)

## UI Features

### File Browser
- **Browse Button**: Opens file selection modal
- **File List**: Shows available heightmap files in your imports folder
- **File Selection**: Click any file to select it for import

### Preview Panel
- **Dimensions Display**: Shows original and scaled dimensions
- **File Information**: Displays filename, size, and format
- **Real-time Updates**: Preview updates automatically when selecting files

### Import Controls
- **Scale Controls**: Adjust height, width, and depth scaling
- **Invert Height**: Option to invert heightmap values
- **Normal Map Generation**: Generate normal maps from heightmaps
- **Import Button**: Process and import the selected heightmap
- **Automatic Tool Switch**: After successful import, automatically switches to the paste tool for immediate placement

### Workflow
1. Open the heightmap dialog with `/heightmap`
2. Browse and select your heightmap file
3. Adjust settings (scale, mode, blocks, etc.)
4. Click **Import** to process the heightmap
5. The mod automatically switches to the **paste tool**
6. Use the paste tool to place your imported terrain

## Configuration

### Scale Settings
- **Width Scale**: Horizontal scaling factor
- **Height Scale**: Vertical scaling factor  
- **Depth Scale**: Depth/intensity scaling factor

### Import Options
- **Invert Height**: Reverses height values (high becomes low, low becomes high)
- **Generate Normals**: Creates normal map data for lighting calculations

## Technical Details

### File Processing
- Heightmaps are processed and scaled according to your settings
- Image data is converted to Hytale's terrain format
- Scaling preserves aspect ratio by default
- Normal maps are generated using Sobel edge detection

### Performance
- Files are processed server-side for consistency
- Preview updates are optimized for real-time feedback
- Large heightmaps are automatically chunked for memory efficiency

## Troubleshooting

### Common Issues

**Files not showing in browser**
- Ensure files are in the correct directory: `Z:\Games\Hytale\UserData\imports\images\`
- Check that files have supported extensions (.png, .bmp, .raw)
- Verify files are not corrupted

**Import fails**
- Check file format compatibility
- Ensure sufficient disk space
- Verify file permissions

**Preview not updating**
- Try selecting a different file
- Check server console for error messages
- Restart the heightmap dialog

### Error Messages

- **"File not found"**: Check file path and ensure file exists
- **"Could not read heightmap"**: File may be corrupted or unsupported format
- **"Error loading heightmap"**: Check file permissions and disk space

## Development

### Building from Source
1. Clone the repository
2. Build using Gradle: `./gradlew build`
3. Place the built JAR in your mods directory

### Contributing
Feel free to submit issues and pull requests for:
- New file format support
- UI improvements
- Performance optimizations
- Bug fixes

## Changelog

### v1.0.0
- Initial release
- Basic heightmap import functionality
- File browser and preview system
- Scale controls and import options

## Support

For issues, bug reports, or feature requests:
- Create an issue on the project repository
- Check the troubleshooting section above
- Review the console logs for detailed error information

## License

This mod is released under the MIT License. See LICENSE file for details.
