/**
 * Shanoir NG - Import, manage and share neuroimaging data
 * Copyright (C) 2009-2019 Inria - https://www.inria.fr/
 * Contact us on https://project.inria.fr/shanoir/
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/gpl-3.0.html
 */
import { Component, forwardRef, Input, OnChanges, SimpleChanges } from '@angular/core';
import { NG_VALUE_ACCESSOR } from '@angular/forms';

import { Study } from '../../../studies/shared/study.model';
import { SubjectStudy } from '../../../subjects/shared/subject-study.model';
import { Subject } from '../../../subjects/shared/subject.model';
import { AbstractInput } from '../../form/input.abstract';
import { Option } from '../../select/select.component';
import { Mode } from '../entity/entity.component.abstract';

@Component({
  selector: 'subject-study-list',
  templateUrl: 'subject-study-list.component.html',
  styleUrls: ['subject-study-list.component.css'],
  providers: [
    { 
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SubjectStudyListComponent),
      multi: true
    }
]
})

export class SubjectStudyListComponent extends AbstractInput implements OnChanges {
    
    @Input() mode: Mode;
    @Input() subject: Subject;
    @Input() study: Study;
    @Input() selectableList: Subject[] | Study[];
    public selected: Subject | Study;
    public optionList: Option<Subject | Study>[];
    @Input() displaySubjectType: boolean = true;
    hasTags: boolean;

    get legend(): string {
        return this.compMode == 'study' ? 'Subjects' : 'Studies';
    }
    
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.selectableList) {
            this.optionList = [];
            if (this.selectableList) {
                for (let item of this.selectableList) {
                    let option: Option<Subject | Study> = new Option(item, item.name);
                    if(this.model && this.model.find(subStu => (this.compMode == 'study' ? subStu.subject.id : subStu.study.id) == option.value.id)) {
                        option.disabled = true;
                    }
                    this.optionList.push(option);
                }
            }
        }
    }
    
    writeValue(obj: any): void {
        super.writeValue(obj);
        this.processHasTags();
        this.updateDisabled();
    }

    private updateDisabled() {
        if (this.selectableList && this.model) {
            if (this.compMode == 'study') {
                for (let option of this.optionList) {
                    if(this.model.find(subStu => subStu.subject.id == option.value.id)) option.disabled = true; 
                }
            } else if (this.compMode == 'subject') {
                for (let option of this.optionList) {
                    if(this.model.find(subStu => subStu.study.id == option.value.id)) option.disabled = true; 
                }
            }
        }
    }

    get compMode(): 'subject' | 'study' { 
        if (this.subject && this.study) throw Error('You cannot set both subject and study');
        if (this.subject) return 'subject';
        if (this.study) return 'study';
        throw Error('You have to set either subject or study');
        
    }

    onAdd() {
        if (!this.selected) return;
        if (this.optionList) {
            let foundOption = this.optionList.find(option => option.value.id == this.selected.id);
            if (foundOption) foundOption.disabled = true;
        }
        let newSubjectStudy: SubjectStudy = new SubjectStudy();
        newSubjectStudy.physicallyInvolved = false;
        newSubjectStudy.tags=[];
        if (this.compMode == "study") {
            let studyCopy: Study = new Study();
            studyCopy.id = this.study.id;
            studyCopy.tags = this.study.tags;
            newSubjectStudy.study = studyCopy;
            newSubjectStudy.subject = this.selected as Subject;
        }
        else if (this.compMode == "subject") {
            let subjectCopy: Subject = new Subject();
            subjectCopy.id = this.subject.id;
            newSubjectStudy.subject = subjectCopy;
            newSubjectStudy.study = this.selected as Study;
        }
        this.selected = undefined;
        this.model.push(newSubjectStudy);
        this.processHasTags();
        this.propagateChange(this.model);
    }

    private processHasTags() {
        this.hasTags = !!this.model && !!(this.model as SubjectStudy[]).find(subStu => subStu.study && subStu.study.tags && subStu.study.tags.length > 0);
    }

    removeSubjectStudy(subjectStudy: SubjectStudy):void {
        const index: number = this.model.indexOf(subjectStudy);
        if (index > -1) {
            this.model.splice(index, 1);
            this.propagateChange(this.model);
            if (this.compMode == 'study') {
                let option: Option<Subject> = this.optionList.find(opt => opt.value.id == subjectStudy.subject.id) as Option<Subject>;
                if (option) option.disabled = false;
            } else if (this.compMode == 'subject') {
                let option: Option<Study> = this.optionList.find(opt => opt.value.id == subjectStudy.study.id) as Option<Study>;
                if (option) option.disabled = false;
            }
        }
    }

    getFontColor(colorInp: string): boolean {
          var color = (colorInp.charAt(0) === '#') ? colorInp.substring(1, 7) : colorInp;
          var r = parseInt(color.substring(0, 2), 16); // hexToR
          var g = parseInt(color.substring(2, 4), 16); // hexToG
          var b = parseInt(color.substring(4, 6), 16); // hexToB
          return (((r * 0.299) + (g * 0.587) + (b * 0.114)) < 145);
    }


    onChange() {
        this.propagateChange(this.model);
        this.propagateTouched();
    }

    onTouch() {
        this.propagateTouched();
    }
}
