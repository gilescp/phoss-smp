--
-- Copyright (C) 2019-2021 Philip Helger and contributors
-- philip[at]helger[dot]com
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--         http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE smp_audit (
  dt         timestamp    NOT NULL,
  actiontype varchar(10)  NOT NULL,
  success    boolean      NOT NULL,
  objtype    varchar(100),
  action     varchar(100),
  args       text
);

COMMENT ON COLUMN smp_audit.dt         IS 'Internal ID';
COMMENT ON COLUMN smp_audit.actiontype IS 'Transport profile name';
COMMENT ON COLUMN smp_audit.success    IS 'Was the action successful or not?';
COMMENT ON COLUMN smp_audit.objtype    IS 'The object type';
COMMENT ON COLUMN smp_audit.action     IS 'The action that was performed';
COMMENT ON COLUMN smp_audit.args       IS 'The arguments of the audit action';
