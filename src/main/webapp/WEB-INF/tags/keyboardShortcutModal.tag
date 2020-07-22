<%@tag description="Tag for changing and displaying the settings of a region" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<div id="kb-shortcut-modal" class="modal">
    <div class="modal-content">
        <h4>LAREX Keyboard Shortcuts</h4>
        <div class="row">
            <div class="col s12">
                <ul class="tabs" id="kb-shortcut-modal-tabs">
                    <li class="tab col s3"><a href="#edit">Edit</a></li>
                    <li class="tab col s3"><a href="#segment">Segments</a></li>
                    <li class="tab col s3"><a href="#lines">Lines</a></li>
                    <li class="tab col s3"><a href="#text">Text</a></li>
                </ul>
            </div>
            <div id="edit" class="col s12">
                <ul class="keyboard-shortcut-list">
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        ?
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Open/Close keyboard shortcut info
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        ←
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Move canvas left
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        →
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Move canvas right
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        ↑
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Move canvas upwards
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        ↓
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Move canvas downwards
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        Space
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Fit zoom
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        +
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Zoom in
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        -
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Zoom out
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        Ctrl
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Toggle multiple element selection mode
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        Shift
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Toggle mouse box selection mode
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        Tab
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Select next element
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>a</span>
                                </td>
                                <td class="kbdtext">
                                    Select all
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>z</span>
                                </td>
                                <td class="kbdtext">
                                    Redo
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>Shift</span>+<span>z</span>
                                </td>
                                <td class="kbdtext">
                                    Undo
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Del</span>
                                </td>
                                <td class="kbdtext">
                                    Delete selected element(s)
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Esc</span>
                                </td>
                                <td class="kbdtext">
                                    Deselect and/or end current editing mode
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>3</span>
                                </td>
                                <td class="kbdtext">
                                    Create segment rectangle
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>4</span>
                                </td>
                                <td class="kbdtext">
                                    Create segment polygon
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>5</span>
                                </td>
                                <td class="kbdtext">
                                    Create cut line
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>7</span>
                                </td>
                                <td class="kbdtext">
                                    Create a subtraction rectangle
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>8</span>
                                </td>
                                <td class="kbdtext">
                                    Create a subtraction polygon
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>6</span>
                                </td>
                                <td class="kbdtext">
                                    Display contours
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>c</span>
                                </td>
                                <td class="kbdtext">
                                    Combine selected
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>f</span>
                                </td>
                                <td class="kbdtext">
                                    Fixate selected
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>r</span>
                                </td>
                                <td class="kbdtext">
                                    Add selected to reading order
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>r</span>
                                </td>
                                <td class="kbdtext">
                                    Toggle reading order editing
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>s</span>
                                </td>
                                <td class="kbdtext">
                                    Export PAGE XML
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Page Up</span>
                                </td>
                                <td class="kbdtext">
                                    Previous page
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Page Down</span>
                                </td>
                                <td class="kbdtext">
                                    Next page
                                </td>
                            </tr>
                        </table>
                    </li>
                </ul>
            </div>
            <div id="segment" class="col s12">
                <ul class="keyboard-shortcut-list">
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        ?
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Open/Close keyboard shortcut info
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        ←
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Move canvas left
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        →
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Move canvas right
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        ↑
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Move canvas upwards
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        ↓
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Move canvas downwards
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        Space
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Fit zoom
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>Space</span>
                                </td>
                                <td class="kbdtext">
                                    Request segmentation
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        +
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Zoom in
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        -
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Zoom out
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        Ctrl
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Toggle multiple element selection mode
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        Shift
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Toggle mouse box selection mode
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        Tab
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Select next element
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>a</span>
                                </td>
                                <td class="kbdtext">
                                    Select all
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>z</span>
                                </td>
                                <td class="kbdtext">
                                    Redo
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>Shift</span>+<span>z</span>
                                </td>
                                <td class="kbdtext">
                                    Undo
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Del</span>
                                </td>
                                <td class="kbdtext">
                                    Delete selected element(s)
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Esc</span>
                                </td>
                                <td class="kbdtext">
                                    Deselect and/or end current editing mode
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>1</span>
                                </td>
                                <td class="kbdtext">
                                    Create area rectangle
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>2</span>
                                </td>
                                <td class="kbdtext">
                                    Create area border
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>3</span>
                                </td>
                                <td class="kbdtext">
                                    Create segment rectangle
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>4</span>
                                </td>
                                <td class="kbdtext">
                                    Create segment polygon
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>5</span>
                                </td>
                                <td class="kbdtext">
                                    Create cut line
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>6</span>
                                </td>
                                <td class="kbdtext">
                                    Display contours
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>7</span>
                                </td>
                                <td class="kbdtext">
                                    Create a subtraction rectangle
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>8</span>
                                </td>
                                <td class="kbdtext">
                                    Create a subtraction polygon
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>c</span>
                                </td>
                                <td class="kbdtext">
                                    Combine selected
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>f</span>
                                </td>
                                <td class="kbdtext">
                                    Fixate selected
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>r</span>
                                </td>
                                <td class="kbdtext">
                                    Add selected to reading order
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>r</span>
                                </td>
                                <td class="kbdtext">
                                    Toggle reading order editing
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>s</span>
                                </td>
                                <td class="kbdtext">
                                    Export PAGE XML
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Page Up</span>
                                </td>
                                <td class="kbdtext">
                                    Previous page
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Page Down</span>
                                </td>
                                <td class="kbdtext">
                                    Next page
                                </td>
                            </tr>
                        </table>
                    </li>
                </ul>
            </div>
            <div id="lines" class="col s12">
                <ul class="keyboard-shortcut-list">
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        ?
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Open/Close keyboard shortcut info
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        ←
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Move canvas left
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        →
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Move canvas right
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        ↑
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Move canvas upwards
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        ↓
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Move canvas downwards
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        Space
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Fit zoom
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>Space</span>
                                </td>
                                <td class="kbdtext">
                                    Request segmentation
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        +
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Zoom in
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        -
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Zoom out
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        Ctrl
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Toggle multiple element selection mode
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        Shift
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Toggle mouse box selection mode
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>
                                        Tab
                                    </span>
                                </td>
                                <td class="kbdtext">
                                    Select next element
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>a</span>
                                </td>
                                <td class="kbdtext">
                                    Select all
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>z</span>
                                </td>
                                <td class="kbdtext">
                                    Redo
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>Shift</span>+<span>z</span>
                                </td>
                                <td class="kbdtext">
                                    Undo
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Del</span>
                                </td>
                                <td class="kbdtext">
                                    Delete selected element(s)
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Esc</span>
                                </td>
                                <td class="kbdtext">
                                    Deselect and/or end current editing mode
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>3</span>
                                </td>
                                <td class="kbdtext">
                                    Create text line rectangle
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>4</span>
                                </td>
                                <td class="kbdtext">
                                    Create text line polygon
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>5</span>
                                </td>
                                <td class="kbdtext">
                                    Create cut line
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>6</span>
                                </td>
                                <td class="kbdtext">
                                    Display contours
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>7</span>
                                </td>
                                <td class="kbdtext">
                                    Create a subtraction rectangle
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>8</span>
                                </td>
                                <td class="kbdtext">
                                    Create a subtraction polygon
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>c</span>
                                </td>
                                <td class="kbdtext">
                                    Combine selected
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>f</span>
                                </td>
                                <td class="kbdtext">
                                    Fixate selected
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>r</span>
                                </td>
                                <td class="kbdtext">
                                    Add selected to reading order
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>r</span>
                                </td>
                                <td class="kbdtext">
                                    Toggle reading order editing
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>s</span>
                                </td>
                                <td class="kbdtext">
                                    Export PAGE XML
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Page Up</span>
                                </td>
                                <td class="kbdtext">
                                    Previous page
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Page Down</span>
                                </td>
                                <td class="kbdtext">
                                    Next page
                                </td>
                            </tr>
                        </table>
                    </li>
                </ul>
            </div>
            <div id="text" class="col s12">
                <ul class="keyboard-shortcut-list">
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Esc</span>
                                </td>
                                <td class="kbdtext">
                                    End editing
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Tab</span>
                                </td>
                                <td class="kbdtext">
                                    Select next
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Alt</span>
                                </td>
                                <td class="kbdtext">
                                    Fade text input in Page View
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Alt</span>+<span>D</span>
                                </td>
                                <td class="kbdtext">
                                    <span>Discard ground truth for the selected text line</span>
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Enter</span>
                                </td>
                                <td class="kbdtext">
                                    Save text line
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>+</span>
                                </td>
                                <td class="kbdtext">
                                    Increase global text zoom
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>-</span>
                                </td>
                                <td class="kbdtext">
                                    Decrease global text zoom
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Space</span>
                                </td>
                                <td class="kbdtext">
                                    Reset global text zoom
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>s</span>
                                </td>
                                <td class="kbdtext">
                                    Export PAGE XML
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>y</span>
                                </td>
                                <td class="kbdtext">
                                    Redo
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Ctrl</span>+<span>z</span>
                                </td>
                                <td class="kbdtext">
                                    Undo
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Page Up</span>
                                </td>
                                <td class="kbdtext">
                                    Previous page
                                </td>
                            </tr>
                        </table>
                    </li>
                    <li>
                        <table>
                            <tr>
                                <td class="kbd">
                                    <span>Page Down</span>
                                </td>
                                <td class="kbdtext">
                                    Next page
                                </td>
                            </tr>
                        </table>
                    </li>
                </ul>
            </div>
        </div>
    </div>
    <div class="modal-footer">
        <a href="#!" class="modal-close waves-effect waves-green btn-flat">Close</a>
    </div>
</div>
