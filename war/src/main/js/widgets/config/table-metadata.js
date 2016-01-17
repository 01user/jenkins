/*
 * Internal support module for config tables.
 */

var jQD = require('jquery-detached');

exports.findConfigTables = function() {
    var $ = jQD.getJQuery();
    // The config tables are the immediate child <table> elements of <form> elements
    // with a name of "config"?
    return $('form[name="config"] > table');
};

exports.decorateConfigTable = function(configTable) {
    var $ = jQD.getJQuery();
    var sectionHeaders = $('.section-header', configTable);

    // Mark the ancestor <tr>s of the section headers and add a title
    sectionHeaders.each(function () {
        var sectionHeader = $(this);
        var sectionRow = sectionHeader.closest('tr');
        var sectionTitle = sectionRow.text();

        // Remove leading hash from accumulated text in title (from <a> element).
        if (sectionTitle.indexOf('#') === 0) {
            sectionTitle = sectionTitle.substring(1);
        }

        sectionRow.addClass('section-header-row');
        sectionRow.attr('title', sectionTitle);
    });

    // Go through the top level <tr> elements (immediately inside the <tbody>)
    // and group the related <tr>s based on the "section-header-row", using a "normalized"
    // version of the section title as the section id.
    var tbody = $('> tbody', configTable);
    var topRows = $('> tr', tbody);
    var curSection = {
        title: 'General'
    };

    var configTableMetadata = new ConfigTableMetaData(configTable, topRows);
    configTableMetadata.sections.push(curSection);
    curSection.id = exports.toId(curSection.title);

    topRows.each(function () {
        var tr = $(this);
        if (tr.hasClass('section-header-row')) {
            // a new section
            var title = tr.attr('title');
            curSection = {
                title: title,
                id: exports.toId(title)
            };
            configTableMetadata.sections.push(curSection);
        }

        tr.addClass(curSection.id);
    });

    var buttonsRow = $('#bottom-sticker', configTable).closest('tr');
    buttonsRow.removeClass(curSection.id);
    buttonsRow.addClass(exports.toId('buttons'));
    
    return configTableMetadata;
};

exports.toId = function(string) {
    string = string.trim();
    return 'config_' + string.replace(/[\W_]+/g, '_').toLowerCase();
};

/*
 * ConfigTable MetaData class.
 */

function ConfigTableMetaData(configTable, topRows) {
    this.configTable = configTable;
    this.topRows = topRows;
    this.sections = [];
    this.$ = jQD.getJQuery();
}

ConfigTableMetaData.prototype.showSection = function(sectionId) {
    this.topRows.hide();
    this.topRows.filter('.' + sectionId).show();

    var $ = jQD.getJQuery();
    // Hide the section header row. No need for it now because the
    // tab text acts as the section label.
    $('.section-header-row').hide();

    // and always show the buttons
    this.topRows.filter('.config_buttons').show();
};

ConfigTableMetaData.prototype.sectionCount = function() {
    return this.sections.length;
};

ConfigTableMetaData.prototype.hasSections = function() {
    var hasSections = (this.sectionCount() > 0);
    if (!hasSections) {
        console.warn('Jenkins configuration without sections?');
    }
    return  hasSections;
};

ConfigTableMetaData.prototype.sectionIds = function() {
    var sectionIds = [];
    for (var i = 0; i < this.sections.length; i++) {
        sectionIds.push(this.sections[i].id);
    }
    return sectionIds;
};

ConfigTableMetaData.prototype.activateSection = function(sectionId) {
    if (!sectionId) {
        throw 'Invalid section id "' + sectionId + '"';
    }

    if (this.hasSections()) {
        for (var i = 0; i < this.sections.length; i++) {
            var section = this.sections[i];
            if (section.id === sectionId) {
                this.sections[i].clicker.click();
                return;
            }
        }
    }
};

ConfigTableMetaData.prototype.activateFirstSection = function() {
    if (this.hasSections()) {
        this.activateSection(this.sections[0].id);
    }
};

ConfigTableMetaData.prototype.addSectionClicker = function(section, clicker) {
    var configTMD = this;
    
    section.clicker = clicker;
    section.clicker.click(function() {
        configTMD.$('.config-section-clicker.active', configTMD.clickerContainer).removeClass('active');
        section.clicker.addClass('active');
        configTMD.showSection(section.id);
    });
};
