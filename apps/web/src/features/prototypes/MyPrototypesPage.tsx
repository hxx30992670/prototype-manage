import React, { useState } from 'react';
import { Segmented } from 'antd';
import { PrototypeListPage } from './PrototypeListPage';

export const MyPrototypesPage: React.FC = () => {
  const [tab, setTab] = useState<'created' | 'owned'>('created');

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Segmented
          options={[
            { label: '我创建的原型', value: 'created' },
            { label: '我负责的原型', value: 'owned' },
          ]}
          value={tab}
          onChange={(val) => setTab(val as 'created' | 'owned')}
          size="large"
        />
      </div>

      {tab === 'created' ? (
        <PrototypeListPage
          key="created"
          title="我创建的原型"
          initialQuery={{ createdBy: 'me' }}
        />
      ) : (
        <PrototypeListPage
          key="owned"
          title="我负责的原型"
          initialQuery={{ ownerId: 'me' }}
        />
      )}
    </div>
  );
};
